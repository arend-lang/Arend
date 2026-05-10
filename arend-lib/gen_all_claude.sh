#!/bin/bash
# Regenerate the entire .aiGuide from .compactifiedLib using Claude CLI.
#
# Three phases:
#   1. Module descriptions — one .md per .ard file (like gen_md_claude.sh)
#   2. Directory READMEs  — one README.md per top-level directory
#   3. Library README     — top-level .aiGuide/README.md
#
# Usage:
#   ./gen_all_claude.sh              # regenerate everything
#   ./gen_all_claude.sh --phase 1    # only module descriptions
#   ./gen_all_claude.sh --phase 2    # only directory READMEs
#   ./gen_all_claude.sh --phase 3    # only library README
#   ./gen_all_claude.sh --dir Algebra # only modules under Algebra/
#   ./gen_all_claude.sh --force       # regenerate even if files exist

set -euo pipefail

BASEDIR="$(cd "$(dirname "$0")" && pwd)"
COMPACTIFIED="$BASEDIR/.compactifiedLib"
AIGUIDE="$BASEDIR/.aiGuide"

PHASE=""
ONLY_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --phase) PHASE="$2"; shift 2 ;;
        --dir)   ONLY_DIR="$2"; shift 2 ;;
        --force) FORCE=1; shift ;;
        *)       echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Helpers ──────────────────────────────────────────────────────────────────

# Good example to show Claude the desired module description format
load_example_module() {
    cat "$AIGUIDE/Paths.md" 2>/dev/null || echo '### Paths
Core path (identity type) operations and lemmas.
#### Basic Path Operations
- **`idpe`**: `idp` as a function: `a = a`.
- **`pmap`**: Congruence: `a = a'"'"' -> f a = f a'"'"'`.'
}

# Good example to show Claude the desired directory README format
load_example_dir_readme() {
    cat "$AIGUIDE/Order/README.md" 2>/dev/null || echo '### Order
This directory formalizes order-theoretic structures.
#### Core Order Structures
- **`PartialOrder.md`** — Preorders and posets as categories.
- **`StrictOrder.md`** — Strict (irreflexive) order structures.'
}

# Good example to show Claude the desired library README format
load_example_lib_readme() {
    cat "$AIGUIDE/README.md" 2>/dev/null || echo '### arend-lib AI Guide Overview
This guide documents the Arend standard library.
#### Foundation
- **`Paths.md`**: Core path operations.
- **`Equiv/`**: Equivalence theory.'
}

call_claude() {
    local prompt="$1"
    shift
    echo "$prompt" | claude -p --no-session-persistence "$@" 2>/dev/null
}

# ── Phase 1: Module descriptions ─────────────────────────────────────────────

phase1_modules() {
    echo "=== Phase 1: Generating module descriptions ==="

    local example_output
    example_output=$(load_example_module)

    local count=0
    local errors=0

    local find_args=()
    if [ -n "$ONLY_DIR" ]; then
        find_args=("$COMPACTIFIED/$ONLY_DIR" -name "*.ard")
    else
        find_args=("$COMPACTIFIED" -name "*.ard")
    fi

    find "${find_args[@]}" | sort | while read -r ardfile; do
        # Compute relative path from .compactifiedLib root
        local relpath="${ardfile#$COMPACTIFIED/}"
        relpath="${relpath%.ard}"

        local mdfile="$AIGUIDE/${relpath}.md"
        local module_name="${relpath//\//.}"

        # Skip if already exists (use --force env var to override)
        if [ -f "$mdfile" ] && [ "${FORCE:-}" != "1" ]; then
            echo "  Skipping (exists): $module_name"
            continue
        fi

        echo "  Processing: $module_name"

        local ard_content
        ard_content=$(cat "$ardfile")

        mkdir -p "$(dirname "$mdfile")"

        local prompt="You are writing a brief reference guide entry for an AI coding agent about an Arend proof assistant library module.

Given the Arend source file for module \`$module_name\`, write a concise markdown description.

Here is an example of the desired format and style:

$example_output

Follow the same style:
- First line: \`### Module.Name\`
- Second line: A short plain-text sentence summarizing the module's mathematical purpose.
- Then a brief conceptual paragraph (2–4 sentences) explaining the key ideas and approach behind the module's constructions — e.g., how the main abstractions relate, what role presentations/covers/localizations play, or why the formalization is structured the way it is. This helps an AI agent understand the design intent, not just the definitions.
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

        if result=$(call_claude "$prompt"); then
            echo "$result" > "$mdfile"
            echo "    -> Written: $mdfile"
            count=$((count + 1))
        else
            echo "    -> ERROR processing $module_name" >&2
            errors=$((errors + 1))
        fi

        sleep 1
    done

    echo "Phase 1 done. Errors: $errors"
}

# ── Phase 2: Directory READMEs ───────────────────────────────────────────────

phase2_dir_readmes() {
    echo "=== Phase 2: Generating directory READMEs ==="

    local example_readme
    example_readme=$(load_example_dir_readme)

    local errors=0

    # Only generate READMEs for top-level directories (not subdirectories)
    local dirs=()
    if [ -n "$ONLY_DIR" ]; then
        dirs=("$ONLY_DIR")
    else
        for d in "$AIGUIDE"/*/; do
            [ -d "$d" ] || continue
            local dirname
            dirname=$(basename "$d")
            dirs+=("$dirname")
        done
    fi

    for reldir in "${dirs[@]}"; do
        local readme="$AIGUIDE/$reldir/README.md"

        if [ -f "$readme" ] && [ "${FORCE:-}" != "1" ]; then
            echo "  Skipping (exists): $reldir/README.md"
            continue
        fi

        echo "  Processing: $reldir/README.md"

        # Collect file list for this directory
        local file_list=""
        for mdfile in "$AIGUIDE/$reldir"/*.md; do
            [ -f "$mdfile" ] || continue
            local basename
            basename=$(basename "$mdfile")
            [ "$basename" = "README.md" ] && continue
            file_list+="- $AIGUIDE/$reldir/$basename\n"
        done

        # Also list subdirectory names (for reference in the README)
        local subdir_list=""
        for subdir in "$AIGUIDE/$reldir"/*/; do
            [ -d "$subdir" ] || continue
            local subname
            subname=$(basename "$subdir")
            subdir_list+="- $subname/\n"
        done

        local prompt="You are writing a directory README for an AI coding agent about the Arend proof assistant standard library.

This README describes the \`$reldir\` directory.

The following module description files are available for you to read (use the Read tool):
$(echo -e "$file_list")

The following subdirectories exist:
$(echo -e "$subdir_list")

Read the module files you need to understand what each module provides, then write the README.

Here is an example of the desired format:

$example_readme

Follow the same style:
- First line: \`### $reldir\`
- Second line: A sentence summarizing what this directory formalizes.
- Group the modules into logical sections using \`#### Section Name\` headers.
- For each module: \`- **\\\`FileName.md\\\`** — One-sentence description.\`
- For subdirectories: \`- **\\\`SubDir/\\\`** — One-sentence description.\`
- Keep it concise — this is a navigation aid for an AI agent.

Write ONLY the markdown content, nothing else."

        mkdir -p "$(dirname "$readme")"

        if result=$(call_claude "$prompt" --allowedTools Read --add-dir "$AIGUIDE/$reldir"); then
            echo "$result" > "$readme"
            echo "    -> Written: $readme"
        else
            echo "    -> ERROR processing $reldir/README.md" >&2
            errors=$((errors + 1))
        fi

        sleep 1
    done

    echo "Phase 2 done. Errors: $errors"
}

# ── Phase 3: Library README ──────────────────────────────────────────────────

phase3_lib_readme() {
    echo "=== Phase 3: Generating library README ==="

    local example_lib_readme
    example_lib_readme=$(load_example_lib_readme)

    local readme="$AIGUIDE/README.md"

    if [ -f "$readme" ] && [ "${FORCE:-}" != "1" ]; then
        echo "  Skipping (exists): README.md (use FORCE=1 to override)"
        echo "Phase 3 done."
        return
    fi

    # Collect file list for root modules
    local root_file_list=""
    for mdfile in "$AIGUIDE"/*.md; do
        [ -f "$mdfile" ] || continue
        local basename
        basename=$(basename "$mdfile")
        [ "$basename" = "README.md" ] && continue
        root_file_list+="- $mdfile\n"
    done

    # Collect directory README list
    local dir_readme_list=""
    for dirreadme in "$AIGUIDE"/*/README.md; do
        [ -f "$dirreadme" ] || continue
        local dirname
        dirname=$(basename "$(dirname "$dirreadme")")
        dir_readme_list+="- $dirreadme ($dirname/)\n"
    done

    local prompt="You are writing the top-level README for an AI coding agent about the Arend proof assistant standard library (\`arend-lib\`).

The following root-level module description files are available for you to read:
$(echo -e "$root_file_list")

The following directory READMEs are available:
$(echo -e "$dir_readme_list")

Read the files you need to understand the library structure, then write the top-level README.

Here is an example of the desired format:

$example_lib_readme

Follow the same style:
- First line: \`### arend-lib AI Guide Overview\`
- Second line: A sentence explaining this is the Arend standard library guide.
- Group modules and directories into thematic sections using \`#### Section Name\` headers (e.g., Foundation, Data Types, Algebra, etc.).
- For root modules: \`- **\\\`FileName.md\\\`**: One-sentence description.\`
- For directories: \`- **\\\`DirName/\\\`**: One-sentence description.\`
- Keep it concise — this is a high-level navigation aid for an AI agent.

Write ONLY the markdown content, nothing else."

    echo "  Processing: README.md"

    if result=$(call_claude "$prompt" --allowedTools Read --add-dir "$AIGUIDE"); then
        echo "$result" > "$readme"
        echo "    -> Written: $readme"
    else
        echo "    -> ERROR processing README.md" >&2
    fi

    echo "Phase 3 done."
}

# ── Main ─────────────────────────────────────────────────────────────────────

case "${PHASE:-all}" in
    1)   phase1_modules ;;
    2)   phase2_dir_readmes ;;
    3)   phase3_lib_readme ;;
    all)
        phase1_modules
        phase2_dir_readmes
        phase3_lib_readme
        ;;
    *)
        echo "Unknown phase: $PHASE (use 1, 2, 3, or omit for all)"
        exit 1
        ;;
esac

echo ""
echo "All done."
