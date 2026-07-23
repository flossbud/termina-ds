#!/usr/bin/env bash
# Generate the Termina DS release keystore. Run once.
# The keystore is stored OUTSIDE the repository and must never be committed.
set -euo pipefail

KEYSTORE_DIR="${HOME}/.termina-ds"
KEYSTORE_PATH="${KEYSTORE_DIR}/release-keystore.jks"

if [ -f "${KEYSTORE_PATH}" ]; then
    echo "Keystore already exists at ${KEYSTORE_PATH} — refusing to overwrite."
    echo "Losing this file means no future build can update an installed APK."
    exit 1
fi

mkdir -p "${KEYSTORE_DIR}"
chmod 700 "${KEYSTORE_DIR}"

docker run --rm -it -v "${KEYSTORE_DIR}:/ks" termina-ds-build:latest \
    keytool -genkeypair -v \
        -keystore /ks/release-keystore.jks \
        -alias termina-ds \
        -keyalg RSA -keysize 4096 -validity 10000

chmod 600 "${KEYSTORE_PATH}"
cat <<EOF

Keystore written to ${KEYSTORE_PATH}

Export these before running tools/build-apk.sh for a release-signed build:

  export ANDROID_KEYSTORE_PATH=${KEYSTORE_PATH}
  export ANDROID_KEYSTORE_PASSWORD=<store password>
  export ANDROID_KEY_ALIAS=termina-ds
  export ANDROID_KEY_PASSWORD=<key password>

Back this file up. It cannot be regenerated.
EOF
