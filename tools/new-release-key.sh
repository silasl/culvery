#!/usr/bin/env bash
# Makes Culvery's release signing key, one step at a time (4c design D4).
# You type every password into keytool itself: none is passed on a command line, printed, or written to the repo.
# On Windows, use tools/new-release-key.ps1 in PowerShell instead.
set -euo pipefail

if [ -z "${KEYTOOL:-}" ]; then
  KEYTOOL=keytool
  if ! command -v keytool >/dev/null && [ -n "${JAVA_HOME:-}" ]; then
    for candidate in "$JAVA_HOME/bin/keytool" "$JAVA_HOME/bin/keytool.exe"; do
      if [ -x "$candidate" ]; then KEYTOOL=$candidate; break; fi
    done
  fi
fi
if ! command -v "$KEYTOOL" >/dev/null; then
  echo "keytool isn't on PATH. Use the JDK 17 that builds Culvery (set KEYTOOL to its keytool)."
  exit 1
fi
RUN=("$KEYTOOL")
if [ -n "${MSYSTEM:-}" ]; then
  # Git Bash's terminal can't hide typing from Java: keytool would show the password as it is typed.
  if command -v winpty >/dev/null; then
    RUN=(winpty "$KEYTOOL")
  else
    echo "In Git Bash keytool would show your password as you type it. Run this instead, in PowerShell:"
    echo "  .\\tools\\new-release-key.ps1"
    exit 1
  fi
fi

ALIAS=culvery
DEFAULT_DIR="$HOME/.culvery"
if ! REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null); then
  echo "Can't find the Culvery repo from this script's folder, so can't check the key stays outside it."
  echo "Run the script from inside a Culvery checkout, with git on PATH."
  exit 1
fi

echo "Culvery release key"
echo
echo "Step 1 of 4: where the key lives."
echo "It must be outside the repo, and backed up somewhere safe: if it is lost, Culvery has to be uninstalled from the"
echo "tablet (losing its setup) and Google needs a new OAuth client."
read -r -p "Folder [$DEFAULT_DIR]: " DIR
DIR=${DIR:-$DEFAULT_DIR}
mkdir -p "$DIR"
DIR=$(cd "$DIR" && pwd -P)
case "$DIR/" in
  "$(cd "$REPO" && pwd -P)/"*) echo "That folder is inside the repo. Choose one outside it."; exit 1 ;;
esac
STORE="$DIR/culvery-release.jks"
if [ -e "$STORE" ]; then
  echo "$STORE already exists; it is left as it is. Move it away first to make a new key."
  exit 1
fi

echo
echo "Step 2 of 4: keytool asks for a keystore password (twice), then a name and organisation (anything, e.g."
echo "\"Culvery\"), then asks you to confirm. Choose a long password and keep it with the backup."
"${RUN[@]}" -genkeypair -v -keystore "$STORE" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000

STORE_FOR_GRADLE=$STORE
if command -v cygpath >/dev/null; then STORE_FOR_GRADLE=$(cygpath -m "$STORE"); fi

echo
echo "Step 3 of 4: add these four lines to $HOME/.gradle/gradle.properties (your own Gradle file, never the repo's),"
echo "putting the password you just chose in place of <password> on both password lines (a PKCS12 key uses the"
echo "keystore's password):"
echo
echo "culvery.release.storeFile=$STORE_FOR_GRADLE"
echo "culvery.release.storePassword=<password>"
echo "culvery.release.keyAlias=$ALIAS"
echo "culvery.release.keyPassword=<password>"
echo
read -r -p "Press Enter once they are saved. "

echo
echo "Step 4 of 4: the key's SHA-1, for the release OAuth client (docs/setup/google-calendar.md, section 4)."
echo "Run this from the repo root and copy the SHA1 line under 'Variant: release':"
echo
echo "  ./gradlew :app:signingReport"
echo
echo "Then back up $STORE and its password."
