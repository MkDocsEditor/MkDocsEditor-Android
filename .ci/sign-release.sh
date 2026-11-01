#!/usr/bin/env bash
#
# Signs the release APK with the key from the RELEASE_* environment variables.
#
#   sign-release.sh <unsigned apk> <signed apk>
#
# Without all four variables it only prints a notice and exits with 0, so forks and repositories
# without the secrets stay green and publish no release. On success it writes signed=true,
# apk=<path> and fingerprint=<SHA-256 of the certificate> to $GITHUB_OUTPUT.
set -euo pipefail

# .ci/debug.keystore, which must never sign a production build
DEBUG_FINGERPRINT="74:A7:45:FA:C2:4A:BF:BD:F0:A7:30:83:18:71:FE:A6:DE:24:6C:6B:D2:E3:F8:62:49:24:27:1F:C4:D3:70:48"

unsigned_apk=$1
signed_apk=$2
output=${GITHUB_OUTPUT:-/dev/null}

for name in RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then
    echo "::notice title=No release published::$name is not set, so the release APK is not signed and not published."
    exit 0
  fi
done

if [[ -n "${APKSIGNER:-}" ]]; then
  apksigner=$APKSIGNER
else
  apksigner="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)/apksigner"
fi

keystore=$(mktemp)
trap 'rm -f "$keystore"' EXIT
printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode --ignore-garbage > "$keystore"

"$apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$RELEASE_KEY_ALIAS" \
  --ks-pass env:RELEASE_KEYSTORE_PASSWORD \
  --key-pass env:RELEASE_KEY_PASSWORD \
  --v4-signing-enabled false \
  --out "$signed_apk" \
  "$unsigned_apk"

# The start of the line depends on the version of apksigner ("Signer #1", "Signer (minSdkVersion=...").
certs=$("$apksigner" verify --print-certs "$signed_apk" 2>/dev/null)
digest=$(printf '%s\n' "$certs" | sed -n 's/^Signer.* certificate SHA-256 digest: //p' | head -n 1)
fingerprint=$(printf '%s' "$digest" | tr '[:lower:]' '[:upper:]' | sed 's/../&:/g; s/:$//')

if [[ -z "$fingerprint" ]]; then
  # only public certificate data
  printf '%s\n' "$certs"
  echo "::error::Could not read the certificate of the signed APK."
  exit 1
fi
if [[ "$fingerprint" == "$DEBUG_FINGERPRINT" ]]; then
  rm -f "$signed_apk"
  echo "::error::The release key is the debug key. Production builds must not be signed with it."
  exit 1
fi

echo "::notice title=Release certificate::SHA-256 $fingerprint"
{
  echo "signed=true"
  echo "apk=$signed_apk"
  echo "fingerprint=$fingerprint"
} >> "$output"
