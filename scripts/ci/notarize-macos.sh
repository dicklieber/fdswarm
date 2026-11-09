#!/usr/bin/env bash
set -euo pipefail

# jpackage signs the enclosed app; sign the outer DMG before submission.
dmg="${1:?Usage: notarize-macos.sh DMG [KEYCHAIN_PROFILE] [SIGNING_IDENTITY]}"
profile="${2:-fdswarm-notary}"
identity="${3:-}"
if [[ -z "$identity" ]]; then
  identity="$(security find-identity -v -p codesigning | awk -F '\"' '/\"Developer ID Application:/ { print $2 }')"
  [[ -n "$identity" && "$identity" != *$'\n'* ]] || {
    echo "Expected one Developer ID Application identity; supply it as the third argument." >&2
    exit 1
  }
fi
[[ -f "$dmg" && "$dmg" == *.dmg ]] || { echo "Expected an existing DMG: $dmg" >&2; exit 1; }
codesign --sign "$identity" --timestamp "$dmg"
codesign --verify --verbose=2 "$dmg"
result="$(mktemp -t fdswarm-notary)"
trap 'rm -f "$result"' EXIT
# Preserve the response beside the artifact for recovery if Apple rejects it.
if ! xcrun notarytool submit "$dmg" --keychain-profile "$profile" --wait --output-format plist > "$result"; then
  cp "$result" "$dmg.notary.plist"
  echo "Notarization submission failed; response saved to $dmg.notary.plist" >&2
  exit 1
fi
cp "$result" "$dmg.notary.plist"
status="$(/usr/libexec/PlistBuddy -c 'Print :status' "$result")"
submission="$(/usr/libexec/PlistBuddy -c 'Print :id' "$result")"
if [[ "$status" != Accepted ]]; then
  xcrun notarytool log "$submission" --keychain-profile "$profile" "$dmg.notary-log.json" || true
  echo "Notarization $status: $submission. See $dmg.notary-log.json" >&2
  exit 1
fi
xcrun stapler staple "$dmg"
xcrun stapler validate "$dmg"
spctl --assess --type open --context context:primary-signature --verbose=2 "$dmg"
echo "Notarized and stapled: $dmg ($submission)"
