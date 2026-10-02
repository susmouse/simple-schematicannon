import re, os, sys, collections

log = sys.argv[1] if len(sys.argv) > 1 else 'port-build6.log'
lines = open(log, encoding='utf-8', errors='replace').read().splitlines()

# group error blocks: error line + following detail lines (符号:, 位置:, code lines)
blocks = []
cur = None
for ln in lines:
    ln = ln.rstrip('\r')
    m = re.match(r'^(.*\.java):(\d+): 错误: (.*)$', ln)
    if m:
        if cur: blocks.append(cur)
        cur = {'file': os.path.basename(m.group(1)), 'line': int(m.group(2)), 'msg': m.group(3), 'detail': []}
    elif cur is not None:
        # detail lines until next file:line or empty separation
        if ln.startswith('\t') or ln.startswith('  ') or ln.startswith('符号') or ln.startswith('位置') or ln.startswith('说明') or re.match(r'^\s*\^', ln):
            cur['detail'].append(ln.strip())
        elif re.match(r'^(.*\.java):(\d+): (错误|警告)', ln):
            pass
        elif ln.strip() == '':
            blocks.append(cur); cur = None
        else:
            cur['detail'].append(ln.strip())
if cur: blocks.append(cur)

out = collections.defaultdict(list)
for b in blocks:
    out[b['file']].append(b)

for f in sorted(out, key=lambda f: -len(out[f])):
    print(f'######## {f} ({len(out[f])} errors) ########')
    seen = set()
    for b in out[f]:
        key = (b['msg'], tuple(b['detail']))
        dup = key in seen
        seen.add(key)
        d = ' | '.join(b['detail'][:4])[:150]
        print(f"  L{b['line']}: {b['msg'][:90]}" + (f"  [{d}]" if d else '') + ('  (dup)' if dup else ''))
    print()
