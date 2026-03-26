#!/usr/bin/env bash
set -euo pipefail

JENKINS_URL="${JENKINS_URL:-http://127.0.0.1:8080}"
JENKINS_USERNAME="${JENKINS_USERNAME:-}"
JENKINS_TOKEN="${JENKINS_TOKEN:-}"
GHO_TOKEN="${GHO_TOKEN:-${GH_TOKEN:-}}"

if [[ -z "$JENKINS_USERNAME" || -z "$JENKINS_TOKEN" || -z "$GHO_TOKEN" ]]; then
  echo "JENKINS_USERNAME, JENKINS_TOKEN, and GHO_TOKEN are required" >&2
  exit 1
fi

crumb_json="$(curl -fsS -u "${JENKINS_USERNAME}:${JENKINS_TOKEN}" "${JENKINS_URL}/crumbIssuer/api/json")"
crumb_field="$(printf '%s' "$crumb_json" | sed -n 's/.*"crumbRequestField":"\([^"]*\)".*/\1/p')"
crumb_value="$(printf '%s' "$crumb_json" | sed -n 's/.*"crumb":"\([^"]*\)".*/\1/p')"

if [[ -z "$crumb_field" || -z "$crumb_value" ]]; then
  echo "Failed to obtain Jenkins crumb" >&2
  exit 1
fi

groovy_script="$(cat <<EOF
import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret

def credentialId = 'parcelpanel-gh-token'
def description = 'ParcelPanel GitHub token for release publishing'
def secretValue = '${GHO_TOKEN}'
def provider = SystemCredentialsProvider.getInstance()
def store = provider.getStore()
def domain = Domain.global()
def replacement = new StringCredentialsImpl(
    CredentialsScope.GLOBAL,
    credentialId,
    description,
    Secret.fromString(secretValue)
)
def existing = provider.getCredentials().find { it.id == credentialId }

if (existing == null) {
    store.addCredentials(domain, replacement)
    println "created:${credentialId}"
} else {
    store.updateCredentials(domain, existing, replacement)
    println "updated:${credentialId}"
}

Jenkins.instance.save()
println 'credential-sync-complete'
EOF
)"

curl -fsS \
  -u "${JENKINS_USERNAME}:${JENKINS_TOKEN}" \
  -H "${crumb_field}: ${crumb_value}" \
  --data-urlencode "script=${groovy_script}" \
  "${JENKINS_URL}/scriptText"
