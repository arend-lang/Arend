#!/bin/bash
# Script to generate .aiGuide/DirName/*.md files from .compactifiedLib/DirName/*.ard files
# using Claude CLI to produce meaningful descriptions instead of mechanical enumerations.

set -euo pipefail

BASEDIR="$(cd "$(dirname "$0")" && pwd)"

SUBDIR="${1:-}"
if [ -n "$SUBDIR" ]; then
    COMPACTIFIED="$BASEDIR/.compactifiedLib/$SUBDIR"
    AIGUIDE="$BASEDIR/.aiGuide/$SUBDIR"
else
    COMPACTIFIED="$BASEDIR/.compactifiedLib"
    AIGUIDE="$BASEDIR/.aiGuide"
fi

# Good example to show Claude the desired format (Paths.md uses sections)
EXAMPLE_OUTPUT=$(cat "$BASEDIR/.aiGuide/Paths.md" 2>/dev/null)

COUNT=0
ERRORS=0

if [ -n "$SUBDIR" ]; then
    find "$COMPACTIFIED" -name "*.ard" | sort
else
    find "$COMPACTIFIED" -maxdepth 1 -name "*.ard" | sort
fi | while read -r ardfile; do
    # Compute relative path like "Linear/VectorSpace"
    relpath="${ardfile#$COMPACTIFIED/}"
    relpath="${relpath%.ard}"
    
    mdfile="$AIGUIDE/${relpath}.md"
    if [ -n "$SUBDIR" ]; then
        module_name="${SUBDIR//\//.}.${relpath//\//.}"
    else
        module_name="${relpath//\//.}"
    fi

    echo "Processing: $module_name"

    # Read the ard file content
    ard_content=$(cat "$ardfile")

    # Create output directory
    mkdir -p "$(dirname "$mdfile")"

    # Build the prompt
    prompt="You are writing a brief reference guide entry for an AI coding agent about an Arend proof assistant library module.

Given the Arend source file for module \`$module_name\`, write a concise markdown description.

Here is an example of the desired format and style:

$EXAMPLE_OUTPUT

Follow the same style:
- First line: \`### Module.Name\`
- Second line: A short plain-text sentence summarizing the module's mathematical purpose.
- **Organize definitions into logical sections** using \`#### Section Name\` headers, grouping related definitions together (as shown in the example).
- Within each section, bullet points for each top-level definition: \`**\\\`Name\\\`**:\` followed by a concise description of what it does, including type signatures, key parameters, and mathematical meaning.
- Describe each definition's PURPOSE — do not just write \"Function\" or \"Lemma\".
- For classes/records, mention what they extend.
- Keep descriptions compact but informative — an AI agent should understand what each definition provides and when to use it.
- Skip purely internal helpers unless they are important.

Here is the Arend source file to describe:

\`\`\`
$ard_content
\`\`\`

Write ONLY the markdown content, nothing else."

    # Call Claude CLI
    if result=$(echo "$prompt" | claude -p --no-session-persistence 2>/dev/null); then
        echo "$result" > "$mdfile"
        echo "  -> Written: $mdfile"
        COUNT=$((COUNT + 1))
    else
        echo "  -> ERROR processing $module_name" >&2
        ERRORS=$((ERRORS + 1))
    fi

    # Small delay to avoid rate limiting
    sleep 1
done

echo ""
echo "Done. Generated files. Errors: $ERRORS"
