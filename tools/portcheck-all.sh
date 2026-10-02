#!/usr/bin/env bash
# Windows-safe wrapper: same as portcheck.sh all but passes sources via @argfile.
set -u
PROJECT="D:/Projects/minecraftMods/schematicannon-port"
JAVAC="D:/Projects/minecraftMods/_tools/jdk25/jdk-25/bin/javac.exe"
CP="$(cat "$PROJECT/compile-classpath.txt" | tr '\\' '/')"
OUT="${TMPDIR:-/tmp}/portjavac"
rm -rf "$OUT"
mkdir -p "$OUT"
ARG="$OUT/args.txt"
: > "$ARG"
echo "-cp" >> "$ARG"
echo "\"$CP\"" >> "$ARG"
echo "-d" >> "$ARG"
echo "\"$OUT\"" >> "$ARG"
find "$PROJECT/src/main/java" -name '*.java' >> "$ARG"
"$JAVAC" -J-Dfile.encoding=UTF-8 -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8 -proc:none -nowarn -Xmaxerrs 20000 -encoding UTF-8 "@$ARG" 2>&1
echo "[exit=$?]"
