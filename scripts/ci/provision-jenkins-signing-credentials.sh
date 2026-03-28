#!/usr/bin/env bash
set -euo pipefail

JENKINS_URL="${JENKINS_URL:-http://127.0.0.1:8080}"
JENKINS_USERNAME="${JENKINS_USERNAME:-}"
JENKINS_TOKEN="${JENKINS_TOKEN:-}"

if [[ -f release-signing.properties ]]; then
  set -a
  source release-signing.properties
  set +a
fi

PARCELPANEL_SIGNING_STORE_FILE="${PARCELPANEL_SIGNING_STORE_FILE:-}"
PARCELPANEL_SIGNING_STORE_PASSWORD="${PARCELPANEL_SIGNING_STORE_PASSWORD:-}"
PARCELPANEL_SIGNING_KEY_ALIAS="${PARCELPANEL_SIGNING_KEY_ALIAS:-}"
PARCELPANEL_SIGNING_KEY_PASSWORD="${PARCELPANEL_SIGNING_KEY_PASSWORD:-}"

if [[ -z "$JENKINS_USERNAME" || -z "$JENKINS_TOKEN" ]]; then
  echo "JENKINS_USERNAME and JENKINS_TOKEN are required" >&2
  exit 1
fi

if [[ -z "$PARCELPANEL_SIGNING_STORE_FILE" || -z "$PARCELPANEL_SIGNING_STORE_PASSWORD" || -z "$PARCELPANEL_SIGNING_KEY_ALIAS" || -z "$PARCELPANEL_SIGNING_KEY_PASSWORD" ]]; then
  echo "ParcelPanel signing values are required. Generate or source release-signing.properties first." >&2
  exit 1
fi

if [[ ! -f "$PARCELPANEL_SIGNING_STORE_FILE" ]]; then
  echo "Keystore not found: $PARCELPANEL_SIGNING_STORE_FILE" >&2
  exit 1
fi

crumb_json="$(curl -fsS -u "${JENKINS_USERNAME}:${JENKINS_TOKEN}" "${JENKINS_URL}/crumbIssuer/api/json")"
crumb_field="$(printf '%s' "$crumb_json" | sed -n 's/.*"crumbRequestField":"\([^"]*\)".*/\1/p')"
crumb_value="$(printf '%s' "$crumb_json" | sed -n 's/.*"crumb":"\([^"]*\)".*/\1/p')"
keystore_b64="$(base64 -w 0 "$PARCELPANEL_SIGNING_STORE_FILE")"

if [[ -z "$crumb_field" || -z "$crumb_value" ]]; then
  echo "Failed to obtain Jenkins crumb" >&2
  exit 1
fi

groovy_script="$(cat <<EOF
import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SecretBytes
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret
import java.util.Base64

def provider = SystemCredentialsProvider.getInstance()
def store = provider.getStore()
def domain = Domain.global()
def credentialsToSync = [
    [
        id: 'parcelpanel-android-keystore',
        description: 'ParcelPanel Android signing keystore',
        value: new FileCredentialsImpl(
            CredentialsScope.GLOBAL,
            'parcelpanel-android-keystore',
            'ParcelPanel Android signing keystore',
            'parcelpanel-release.keystore',
            SecretBytes.fromRawBytes(Base64.decoder.decode('${keystore_b64}'))
        )
    ],
    [
        id: 'parcelpanel-android-store-password',
        description: 'ParcelPanel Android keystore password',
        value: new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            'parcelpanel-android-store-password',
            'ParcelPanel Android keystore password',
            Secret.fromString('${PARCELPANEL_SIGNING_STORE_PASSWORD}')
        )
    ],
    [
        id: 'parcelpanel-android-key-alias',
        description: 'ParcelPanel Android signing key alias',
        value: new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            'parcelpanel-android-key-alias',
            'ParcelPanel Android signing key alias',
            Secret.fromString('${PARCELPANEL_SIGNING_KEY_ALIAS}')
        )
    ],
    [
        id: 'parcelpanel-android-key-password',
        description: 'ParcelPanel Android signing key password',
        value: new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            'parcelpanel-android-key-password',
            'ParcelPanel Android signing key password',
            Secret.fromString('${PARCELPANEL_SIGNING_KEY_PASSWORD}')
        )
    ]
]

credentialsToSync.each { entry ->
    def existing = provider.getCredentials().find { it.id == entry.id }
    if (existing == null) {
        store.addCredentials(domain, entry.value)
        println "created:" + entry.id
    } else {
        store.updateCredentials(domain, existing, entry.value)
        println "updated:" + entry.id
    }
}

Jenkins.instance.save()
println 'signing-credential-sync-complete'
EOF
)"

curl -fsS \
  -u "${JENKINS_USERNAME}:${JENKINS_TOKEN}" \
  -H "${crumb_field}: ${crumb_value}" \
  --data-urlencode "script=${groovy_script}" \
  "${JENKINS_URL}/scriptText"
