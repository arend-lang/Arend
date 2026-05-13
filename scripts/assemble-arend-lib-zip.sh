#!/usr/bin/env bash
#
# Assembles arend-lib.zip from the arend-lib directory.
#
# Layout inside the zip:
#   arend.yaml                    — library config
#   src/**/*.ard                  — Arend source files
#   ext/**/*.class                — compiled meta-extension classes
#   .aiGuide/**                   — AI guide markdown files
#   .compactifiedLib/**/*.ard     — compactified library sources
#
# Usage:
#   ./scripts/assemble-arend-lib-zip.sh [OUTPUT_PATH]
#
# If OUTPUT_PATH is not specified, the zip is written to arend-lib.zip in the project root.
# The script must be run from the Arend project root directory (the one containing arend-lib/).

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AREND_LIB="$PROJECT_ROOT/arend-lib"
OUTPUT="${1:-$PROJECT_ROOT/arend-lib.zip}"

# Resolve OUTPUT to absolute path
case "$OUTPUT" in
  /*) ;;
  *) OUTPUT="$(pwd)/$OUTPUT" ;;
esac

if [ ! -d "$AREND_LIB" ]; then
  echo "Error: arend-lib directory not found at $AREND_LIB" >&2
  exit 1
fi

cd "$AREND_LIB"

# Ensure ext/ has compiled classes; fall back to meta build output if needed
EXT_HAS_CLASSES=false
if [ -d ext ]; then
  # Use cd into ext to avoid macOS extended-attribute issues with find on full paths
  count=$(cd ext && find . -name '*.class' 2>/dev/null | head -1 | wc -l)
  [ "$count" -gt 0 ] && EXT_HAS_CLASSES=true
fi

if [ "$EXT_HAS_CLASSES" = false ]; then
  META_CLASSES="meta/build/classes/java/main"
  if [ -d "$META_CLASSES" ]; then
    echo "Populating ext/ from meta build output..."
    (cd "$META_CLASSES" && find . -name '*.class' -print0 | while IFS= read -r -d '' f; do
      dest="$AREND_LIB/ext/${f#./}"
      mkdir -p "$(dirname "$dest")"
      cp "$f" "$dest"
    done)
  else
    echo "Warning: No extension classes found. Build the meta module first: ./gradlew :arend-lib:meta:classes" >&2
  fi
fi

# Remove old zip if it exists
rm -f "$OUTPUT"

echo "Creating $OUTPUT ..."

# Build the zip from arend-lib directory
COMPONENTS=""
for dir in ext src .aiGuide .compactifiedLib; do
  [ -d "$dir" ] && COMPONENTS="$COMPONENTS $dir"
done

zip -r -q "$OUTPUT" arend.yaml $COMPONENTS

echo "Done. Created $OUTPUT ($(du -h "$OUTPUT" | cut -f1))"
