import re, collections, os, sys
clusters = {}
for name in ['render','gui','data','network']:
    for ln in open(f'_tools/clusters/{name}.txt', encoding='utf-8'):
        ln = ln.strip()
        if ln.endswith('.java'):
            clusters[os.path.basename(ln)] = name

log = sys.argv[1] if len(sys.argv) > 1 else 'port-build5.log'
lines = open(log, encoding='utf-8', errors='replace').read().splitlines()
errs = []
for ln in lines:
    m = re.match(r'^(.*\.java):(\d+): (错误|警告): (.*)$', ln.rstrip('\r'))
    if m and m.group(3) == '错误':
        errs.append((os.path.basename(m.group(1)), int(m.group(2)), m.group(4)))

print('total errors:', len(errs))
byc = collections.Counter(clusters.get(e[0], 'core') for e in errs)
print('by cluster:', dict(byc))
print()
print('=== top files ===')
c = collections.Counter(e[0] for e in errs)
for k, v in c.most_common(25):
    print(f'{v:4d}  {k}  [{clusters.get(k, "core")}]')
print()
print('=== top messages ===')
m2 = collections.Counter(e[2][:55] for e in errs)
for k, v in m2.most_common(20):
    print(f'{v:4d}  {k}')
print()
print('=== core files (mine) ===')
core = collections.Counter(e[0] for e in errs if clusters.get(e[0], 'core') == 'core')
for k, v in core.most_common(30):
    print(f'{v:4d}  {k}')
