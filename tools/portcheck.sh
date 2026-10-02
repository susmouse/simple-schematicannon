#!/usr/bin/env bash
# Fast isolated javac check for the schematicannon port project.
# Compiles the given files/directories against the Minecraft/NeoForge classpath produced by
# ModDevGradle, without touching Gradle. Directories are expanded recursively.
#
#   portcheck.sh <file-or-dir> [more files or dirs...]
#   portcheck.sh all                 # compile the whole mod source tree
#
set -u
PROJECT="D:/Projects/minecraftMods/schematicannon-port"
JAVAC="D:/Projects/minecraftMods/_tools/jdk25/jdk-25/bin/javac.exe"
CP="$(cat "$PROJECT/compile-classpath.txt")"
OUT="${TMPDIR:-/tmp}/portjavac"
rm -rf "$OUT"
mkdir -p "$OUT"

declare -a FILES=()
for arg in "$@"; do
    if [ "$arg" = "all" ]; then
        arg="$PROJECT/src/main/java"
    fi
    if [ -d "$arg" ]; then
        while IFS= read -r f; do FILES+=("$f"); done < <(find "$arg" -name '*.java')
    else
        FILES+=("$arg")
    fi
done

# Windows has a short command-line limit; when many files are given, pass them via @argfile.
if [ "${#FILES[@]}" -gt 20 ]; then
    ARGFILE="$(dirname "$OUT")/portjavac-args.txt"
    printf '%s\n' "${FILES[@]}" > "$ARGFILE"
    "$JAVAC" -proc:none -nowarn -Xmaxerrs 2000 -encoding UTF-8 -cp "$CP" -d "$OUT" "@$ARGFILE" 2>&1
    echo "[exit=$?]"
    exit 0
fi

"$JAVAC" -proc:none -nowarn -Xmaxerrs 2000 -encoding UTF-8 -cp "$CP" -d "$OUT" "${FILES[@]}" 2>&1
echo "[exit=$?]"
