#!/usr/bin/env bash
# Filter javac output (UTF-8) to only errors whose source file matches one of the
# paths listed in the given file (suffix match after com/bikerboys/schematicannon/).
LOG="${1:-/tmp/pc-all.txt}"
LIST="${2:-D:/Projects/minecraftMods/_tools/render-files.txt}"
PREFIX="com/bikerboys/schematicannon/"
python3 - "$LOG" "$LIST" "$PREFIX" <<'PY'
import sys, re, os
log, lst, prefix = sys.argv[1], sys.argv[2], sys.argv[3]
files = [l.strip().replace('\\','/') for l in open(lst) if l.strip()]
full = set()
for f in files:
    full.add(prefix + f)
# normalize log to forward slashes
lines = open(log, encoding='utf-8', errors='replace').read().replace('\\','/').splitlines()
out = []
cur = None
for line in lines:
    m = re.match(r'^(.*\.java):(\d+): 错误: (.*)$', line)
    if m:
        path = m.group(1)
        rel = path.split(prefix)[-1] if prefix in path else path
        if rel in files:
            cur = True
            out.append(line)
        else:
            cur = False
    elif cur:
        # continuation lines of a matched error
        if re.match(r'^\s', line) or line.strip()=='':
            out.append(line)
        else:
            cur = False
print('\n'.join(out))
PY
