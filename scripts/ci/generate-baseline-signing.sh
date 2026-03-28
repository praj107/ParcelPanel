#!/usr/bin/env bash
set -euo pipefail

SECRETS_DIR="${SECRETS_DIR:-.secrets}"
KEYSTORE_PATH="${PARCELPANEL_SIGNING_STORE_FILE:-${SECRETS_DIR}/parcelpanel-release.keystore}"
PROPERTIES_PATH="${SIGNING_PROPERTIES_PATH:-release-signing.properties}"
KEY_ALIAS="${PARCELPANEL_SIGNING_KEY_ALIAS:-parcelpanel-release}"
STORE_PASSWORD="${PARCELPANEL_SIGNING_STORE_PASSWORD:-$(openssl rand -hex 16)}"
KEY_PASSWORD="${PARCELPANEL_SIGNING_KEY_PASSWORD:-$STORE_PASSWORD}"
DISTINGUISHED_NAME="${PARCELPANEL_SIGNING_DNAME:-CN=ParcelPanel Release, OU=Solo Dev, O=ParcelPanel, L=Perth, ST=Western Australia, C=AU}"

if [[ -f "$KEYSTORE_PATH" && "${FORCE_REGENERATE_SIGNING:-false}" != "true" ]]; then
  echo "Keystore already exists at $KEYSTORE_PATH" >&2
  echo "Use FORCE_REGENERATE_SIGNING=true if you intentionally want to replace it." >&2
  exit 1
fi

if [[ "$KEY_PASSWORD" != "$STORE_PASSWORD" ]]; then
  echo "PKCS12 signing requires the key password to match the store password." >&2
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"

keytool -genkeypair \
  -keystore "$KEYSTORE_PATH" \
  -storetype PKCS12 \
  -storepass "$STORE_PASSWORD" \
  -alias "$KEY_ALIAS" \
  -keypass "$KEY_PASSWORD" \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000 \
  -dname "$DISTINGUISHED_NAME"

cat > "$PROPERTIES_PATH" <<EOF
PARCELPANEL_SIGNING_STORE_FILE=${KEYSTORE_PATH}
PARCELPANEL_SIGNING_STORE_PASSWORD=${STORE_PASSWORD}
PARCELPANEL_SIGNING_KEY_ALIAS=${KEY_ALIAS}
PARCELPANEL_SIGNING_KEY_PASSWORD=${KEY_PASSWORD}
EOF

echo "Created keystore: $KEYSTORE_PATH"
echo "Wrote local signing properties: $PROPERTIES_PATH"
echo "Keystore certificate fingerprints:"
keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" | sed -n '/SHA1:/p;/SHA256:/p'
