#!/usr/bin/env bash
set -euo pipefail

JENKINS_URL="${JENKINS_URL:-http://127.0.0.1:8080}"
JENKINS_USERNAME="${JENKINS_USERNAME:-}"
JENKINS_TOKEN="${JENKINS_TOKEN:-}"
GHO_TOKEN="${GHO_TOKEN:-${GH_TOKEN:-}}"
GITHUB_USERNAME="${GITHUB_USERNAME:-${GH_USERNAME:-${GHO_USERNAME:-praj107}}}"

if [[ -z "$JENKINS_USERNAME" || -z "$JENKINS_TOKEN" || -z "$GHO_TOKEN" || -z "$GITHUB_USERNAME" ]]; then
  echo "JENKINS_USERNAME, JENKINS_TOKEN, GHO_TOKEN, and GITHUB_USERNAME are required" >&2
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
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret

def provider = SystemCredentialsProvider.getInstance()
def store = provider.getStore()
def domain = Domain.global()
def releaseCredentialId = 'parcelpanel-gh-token'
def gitCredentialId = 'parcelpanel-git-http'
def tokenDescription = 'ParcelPanel GitHub token for release publishing'
def gitDescription = 'ParcelPanel Git HTTPS credential for Jenkins checkout and pushes'
def githubUsername = '${GITHUB_USERNAME}'
def secretValue = '${GHO_TOKEN}'

def releaseReplacement = new StringCredentialsImpl(
    CredentialsScope.GLOBAL,
    releaseCredentialId,
    tokenDescription,
    Secret.fromString(secretValue)
)
def gitReplacement = new UsernamePasswordCredentialsImpl(
    CredentialsScope.GLOBAL,
    gitCredentialId,
    gitDescription,
    githubUsername,
    secretValue
)
def releaseExisting = provider.getCredentials().find { it.id == releaseCredentialId }
def gitExisting = provider.getCredentials().find { it.id == gitCredentialId }

if (releaseExisting == null) {
    store.addCredentials(domain, releaseReplacement)
    println "created:" + releaseCredentialId
} else {
    store.updateCredentials(domain, releaseExisting, releaseReplacement)
    println "updated:" + releaseCredentialId
}

if (gitExisting == null) {
    store.addCredentials(domain, gitReplacement)
    println "created:" + gitCredentialId
} else {
    store.updateCredentials(domain, gitExisting, gitReplacement)
    println "updated:" + gitCredentialId
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
