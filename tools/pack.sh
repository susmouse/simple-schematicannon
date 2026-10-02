#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Schematicannon 打包脚本
#
#   tools/pack.sh                完整打包：校验 -> 编译 -> 出 jar -> 校验 jar -> 复制
#   tools/pack.sh --quick        跳过贴图预览图（仍做全部校验）
#   tools/pack.sh --check        只做校验（模型 + 贴图），不编译、不生成预览
#   tools/pack.sh --preview      只生成贴图预览 + 白底模型预览，不编译
#   tools/pack.sh --dist DIR     打包后把 jar 额外复制到 DIR
#   tools/pack.sh --online       允许联网解析依赖（默认离线，快且稳）
#   tools/pack.sh --no-verify    跳过 jar 内容校验（不建议）
#
# 校验项（任一失败即中止，非零退出）：
#   1. 模型 JSON 可解析
#   2. 每个 element 旋转后的 AABB 仍在 [0..16]（旋转角度必须是 22.5 的倍数）
#   3. 贴图引用可解析、文件存在、尺寸 16x16、真彩色
#   4. jar 内贴图与磁盘一致、模型 JSON 与磁盘一致
#
# 依赖：bash、JDK（JAVA_HOME，默认 /d/installation/jdk21）、unzip
# ---------------------------------------------------------------------------
set -euo pipefail

PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOD_DIR="$PROJ_ROOT"
ASSETS="$MOD_DIR/src/main/resources/assets/schematicannon"

MOD_VERSION="$(sed -n 's/^mod_version=//p' "$MOD_DIR/gradle.properties" | tr -d '\r')"
: "${MOD_VERSION:=1.0.0}"
JAR_NAME="schematicannon-${MOD_VERSION}.jar"
JAR_OUT="$PROJ_ROOT/$JAR_NAME"
JAVA_HOME_WIN="${JAVA_HOME:-/d/installation/jdk21}"
export JAVA_HOME="$JAVA_HOME_WIN"

# ---- 参数 ----------------------------------------------------------------
QUICK=0; CHECK_ONLY=0; PREVIEW_ONLY=0; NO_VERIFY=0; ONLINE=0; DIST_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --quick)      QUICK=1 ;;
    --check)      CHECK_ONLY=1 ;;
    --preview)    PREVIEW_ONLY=1 ;;
    --no-verify)  NO_VERIFY=1 ;;
    --online)     ONLINE=1 ;;
    --dist)       shift; DIST_DIR="${1:-}" ;;
    -h|--help)    sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
  shift
done

hr() { printf '%s\n' "------------------------------------------------------------"; }
die() { printf '\n!! %s\n' "$1" >&2; exit 1; }

# 让最后一个失败点能被 main 感知，避免 set -e 被管道吞掉。
FAILED=0
fail() { FAILED=1; printf '\n!! %s\n' "$1" >&2; }

# 找一个能用的 python（优先装过 Pillow 的 venv，其次任意 python）
PY=""
for cand in \
    "C:/Users/Administrator/.workbuddy/binaries/python/envs/default/Scripts/python.exe" \
    "C:/Users/Administrator/.workbuddy/binaries/python/versions/3.13.12/python.exe" \
    python3 python
do
  if command -v "$cand" >/dev/null 2>&1 || [ -x "$cand" ]; then
    if "$cand" -c 'import sys; sys.exit(0 if sys.version_info>=(3,8) else 1)' 2>/dev/null; then
      PY="$cand"; break
    fi
  fi
done
[ -n "$PY" ] || die "找不到可用的 python 解释器"

# ---- 1. 模型校验 ----------------------------------------------------------
verify_models() {
  hr; echo "[1/5] 校验模型 (JSON / 旋转角度 / 旋转后 AABB / 贴图引用)"
  if ! "$PY" "$PROJ_ROOT/tools/_verify_cannon_model.py"; then
    fail "模型校验未通过"
    return 1
  fi
}

# ---- 2. 贴图校验 ----------------------------------------------------------
verify_textures() {
  hr; echo "[2/5] 校验贴图尺寸与格式"
  "$PY" - "$ASSETS/textures" <<'PY' || { fail "贴图尺寸/格式校验未通过"; return 1; }
import os, struct, sys
root = sys.argv[1]
bad = 0
rows = []
for sub in ('block', 'item'):
    d = os.path.join(root, sub)
    if not os.path.isdir(d):
        print(f'  缺少目录: {sub}/'); bad = 1; continue
    for n in sorted(os.listdir(d)):
        if not n.endswith('.png'):
            print(f'  非 png 文件: {sub}/{n}'); bad = 1; continue
        p = os.path.join(d, n)
        head = open(p, 'rb').read(26)
        if head[:8] != b'\x89PNG\r\n\x1a\n':
            print(f'  不是合法 PNG: {sub}/{n}'); bad = 1; continue
        w, h = struct.unpack('>II', head[16:24])
        ct = head[25]
        note = ''
        if (w, h) != (16, 16):
            note = '  <-- 尺寸不是 16x16'; bad = 1
        if ct not in (2, 6):
            note += '  <-- 建议用 RGB/RGBA 真彩色'; bad = 1
        rows.append(f'  {sub + "/" + n:44s} {w}x{h}{note}')
for r in rows:
    print(r)
if bad:
    sys.exit(1)
print(f'  全部 {len(rows)} 张贴图尺寸与格式正常')
PY

  # 模型引用的贴图必须存在
  # 注意：element 的 face 里写的是 "#metal" 这种材质变量，真正的贴图路径在顶层
  # textures 块里。所以必须解析 JSON，不能拿正则去扫 "texture": "schematicannon:..."。
  "$PY" - "$ASSETS" <<'PY' || { fail "模型贴图引用校验未通过"; return 1; }
import json, os, sys
assets = sys.argv[1]
models_dir = os.path.join(assets, 'models')
tex_dir = os.path.join(assets, 'textures')

refs = set()       # 'block/xxx.png'
unresolved = []    # '#foo' 变量没在 textures 里定义

for sub in ('block', 'item'):
    d = os.path.join(models_dir, sub)
    if not os.path.isdir(d):
        continue
    for n in sorted(os.listdir(d)):
        if not n.endswith('.json'):
            continue
        path = os.path.join(d, n)
        rel = f'{sub}/{n}'
        try:
            model = json.load(open(path, encoding='utf-8'))
        except Exception as e:
            print(f'  {rel}: JSON 解析失败: {e}')
            sys.exit(1)

        declared = model.get('textures', {}) or {}

        def resolve(val):
            """把 '#metal' / 'schematicannon:block/x' 解析成 'block/x.png'，失败返回 None。"""
            if not isinstance(val, str):
                return None
            if val.startswith('#'):
                if val[1:] not in declared:
                    unresolved.append(f'{rel}: {val} 未在 textures 中定义')
                    return None
                return resolve(declared[val[1:]])
            if not val.startswith('schematicannon:'):
                return None          # minecraft: 之类的原版贴图，不归我们管
            return val.split(':', 1)[1] + '.png'

        for v in declared.values():
            r = resolve(v)
            if r:
                refs.add(r)
        for el in model.get('elements', []) or []:
            for face in (el.get('faces', {}) or {}).values():
                if isinstance(face, dict) and 'texture' in face:
                    r = resolve(face['texture'])
                    if r:
                        refs.add(r)

if unresolved:
    for u in unresolved:
        print(f'  {u}')
    sys.exit(1)

missing = sorted(r for r in refs if not os.path.isfile(os.path.join(tex_dir, r)))
if missing:
    for m in missing:
        print(f'  模型引用了不存在的贴图: {m}')
    sys.exit(1)
print(f'  模型引用的 {len(refs)} 张贴图全部存在')

orphans = []
for sub in ('block', 'item'):
    d = os.path.join(tex_dir, sub)
    for n in sorted(os.listdir(d)) if os.path.isdir(d) else []:
        if n.endswith('.png') and (sub + '/' + n) not in refs:
            orphans.append(sub + '/' + n)
if orphans:
    print('  提示: 以下贴图没有被任何模型引用（可能是旧文件，或模型还没改过来）')
    for o in orphans:
        print(f'    {o}')
else:
    print('  没有未引用的贴图')
PY
}

# ---- 3. 预览图 ------------------------------------------------------------
render_previews() {
  hr; echo "[3/5] 生成预览图"
  local sheet="$PROJ_ROOT/textures_sheet.png"
  if [ -f "$PROJ_ROOT/tools/tex_preview.py" ]; then
    "$PY" "$PROJ_ROOT/tools/tex_preview.py" "$ASSETS/textures" "$sheet"
    echo "  贴图总览: $sheet"
  fi
  if [ -f "$PROJ_ROOT/tools/_render_white.py" ]; then
    local m="$PROJ_ROOT/model_preview.png"
    "$PY" "$PROJ_ROOT/tools/_render_white.py" "$m" >/dev/null
    echo "  模型预览: $m"
  fi
}

# ---- 4. 编译打包 ----------------------------------------------------------
build_jar() {
  hr; echo "[4/5] 编译并打包"
  cd "$MOD_DIR"
  local gradle_args=(jar --console=plain)
  [ "$ONLINE" = 1 ] || gradle_args+=(--offline)
  # 注意：不能写 `... | tail -8`，管道会吞掉 gradlew 的退出码。
  # 用 PIPESTATUS[0] 取第一个命令的真实状态。
  set +e
  ./gradlew "${gradle_args[@]}" 2>&1 | tail -8
  local gst=${PIPESTATUS[0]}
  set -e
  if [ "$gst" != 0 ]; then
    fail "gradle 编译失败 (exit=$gst)"
    return 1
  fi
  local src="$MOD_DIR/build/libs/$JAR_NAME"
  [ -f "$src" ] || { fail "没有产出 jar: $src"; return 1; }
  cp "$src" "$JAR_OUT"
  local size
  size=$(stat -c '%s' "$JAR_OUT" 2>/dev/null || stat -f '%z' "$JAR_OUT")
  echo "  jar: $JAR_OUT  (${size} 字节)"
}

# ---- 5. 校验 jar 内容 -----------------------------------------------------
verify_jar() {
  hr; echo "[5/5] 校验 jar 内容"
  command -v unzip >/dev/null 2>&1 || { echo "  跳过（没有 unzip）"; return 0; }

  local manifest
  manifest=$(unzip -l "$JAR_OUT" | awk '/assets\/schematicannon\/textures\/.*\.png/ {print $1, $4}')
  local n
  n=$(printf '%s\n' "$manifest" | grep -c . || true)
  echo "  jar 里共 $n 张贴图"

  local drift=0 size path name disk dsize
  while read -r size path; do
    [ -n "${path:-}" ] || continue
    name=${path#assets/schematicannon/textures/}
    disk="$ASSETS/textures/$name"
    if [ -f "$disk" ]; then
      dsize=$(stat -c '%s' "$disk" 2>/dev/null || stat -f '%z' "$disk")
      if [ "$size" != "$dsize" ]; then
        echo "  与磁盘不一致: $name (jar=$size disk=$dsize)"; drift=1
      fi
    else
      echo "  jar 里有磁盘上已删除的贴图: $name"; drift=1
    fi
  done <<< "$manifest"

  # 模型 JSON 也必须是最新的（模型改错是这几轮最主要的坑）
  local jpath="assets/schematicannon/models/block/schematicannon.json"
  if unzip -l "$JAR_OUT" | grep -q "$jpath"; then
    local jar_md5 disk_md5
    jar_md5=$(unzip -p "$JAR_OUT" "$jpath" | md5sum | awk '{print $1}')
    disk_md5=$(md5sum "$MOD_DIR/src/main/resources/$jpath" | awk '{print $1}')
    if [ "$jar_md5" != "$disk_md5" ]; then
      echo "  jar 内模型与磁盘不一致（gradle 没重新打包？）"; drift=1
    else
      echo "  jar 内模型 JSON 与磁盘一致"
    fi
  fi

  if [ "$drift" = 0 ]; then
    echo "  jar 内容校验通过"
  else
    fail "jar 内容与磁盘不一致"
    return 1
  fi
}

# ---- main ----------------------------------------------------------------
echo "Schematicannon 打包"
echo "  项目: $MOD_DIR"
echo "  版本: $MOD_VERSION   ->  $JAR_NAME"
echo "  JDK : $JAVA_HOME"
echo "  Python: $PY"

if [ "$PREVIEW_ONLY" = 1 ]; then
  verify_models || { hr; echo "校验失败"; exit 1; }
  render_previews
  hr; echo "完成（仅预览）"
  exit 0
fi

if [ "$CHECK_ONLY" = 1 ]; then
  verify_models   || { hr; echo "校验失败"; exit 1; }
  verify_textures || { hr; echo "校验失败"; exit 1; }
  hr; echo "完成（仅校验，未编译）"
  exit 0
fi

verify_models   || { hr; echo "中止：模型校验失败"; exit 1; }
verify_textures || { hr; echo "中止：贴图校验失败"; exit 1; }
[ "$QUICK" = 1 ] || render_previews
build_jar       || { hr; echo "中止：编译失败"; exit 1; }
if [ "$NO_VERIFY" != 1 ]; then
  verify_jar || { hr; echo "中止：jar 校验失败"; exit 1; }
fi

if [ -n "$DIST_DIR" ]; then
  hr; echo "[+] 复制到 $DIST_DIR"
  [ -d "$DIST_DIR" ] || mkdir -p "$DIST_DIR"
  cp "$JAR_OUT" "$DIST_DIR/$JAR_NAME"
  echo "  $(cd "$DIST_DIR" && pwd)/$JAR_NAME"
fi

hr; echo "完成"
