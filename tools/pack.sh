#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Schematicannon 打包脚本
#
#   tools/pack.sh              完整打包：编译 -> 出 jar -> 复制到项目根目录 -> 预览贴图
#   tools/pack.sh --quick      跳过贴图预览
#   tools/pack.sh --preview    只生成贴图预览，不编译
#   tools/pack.sh --check      只校验贴图尺寸/命名/引用，不编译
#
# 更新材质后直接跑一次即可：贴图是资源文件，脚本会重新打进 jar。
# ---------------------------------------------------------------------------
set -euo pipefail

PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# mod 工程就在仓库根（build.gradle / src / gradlew 与 tools/ 同级）
MOD_DIR="$PROJ_ROOT"
ASSETS="$MOD_DIR/src/main/resources/assets/schematicannon"
JAR_OUT="$PROJ_ROOT/schematicannon-1.0.0.jar"
JAVA_HOME_WIN="${JAVA_HOME:-/d/installation/jdk21}"

export JAVA_HOME="$JAVA_HOME_WIN"

# ---- 参数 ----------------------------------------------------------------
QUICK=0; PREVIEW_ONLY=0; CHECK_ONLY=0
for a in "$@"; do
  case "$a" in
    --quick)   QUICK=1 ;;
    --preview) PREVIEW_ONLY=1 ;;
    --check)   CHECK_ONLY=1 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "未知参数: $a"; exit 2 ;;
  esac
done

hr() { printf '%s\n' "------------------------------------------------------------"; }

# ---- 1. 贴图校验 ----------------------------------------------------------
check_textures() {
  hr; echo "[1/4] 校验贴图"
  local fail=0

  # 1a. 尺寸与格式
  python3 - "$ASSETS/textures" <<'PY' || fail=1
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

  # 1b. 模型里引用的贴图必须存在
  python3 - "$ASSETS" <<'PY' || fail=1
# 收集模型引用的贴图，同时标记哪些模型是「引用了方块模型、自己不含贴图」的物品模型。
import json, os, re, sys
assets = sys.argv[1]
models_dir = os.path.join(assets, 'models')


def refs_in(path):
    txt = open(path, encoding='utf-8').read()
    # 只取贴图引用：出现在 parent / model / texture 这类键上，指向 minecraft: 的都要忽略。
    return {m.group(1) + '/' + m.group(2) + '.png'
            for m in re.finditer(r'"(?:texture|layer\d+|particle|up|down|north|south|east|west)"\s*:\s*'
                                 r'"schematicannon:(block|item)/([A-Za-z0-9_]+)"', txt)}


# 方块模型直接参与贴图引用；物品模型若只是 parent 方块模型则自然没有任何贴图键
refs = set()
for sub in ('block', 'item'):
    d = os.path.join(models_dir, sub)
    for n in sorted(os.listdir(d)) if os.path.isdir(d) else []:
        if n.endswith('.json'):
            refs |= refs_in(os.path.join(d, n))

missing = sorted(r for r in refs
                 if not os.path.isfile(os.path.join(assets, 'textures', r)))
if missing:
    for m in missing:
        print(f'  模型引用了不存在的贴图: {m}')
    sys.exit(1)
print(f'  模型引用的 {len(refs)} 张贴图全部存在')

orphans = []
for sub in ('block', 'item'):
    d = os.path.join(assets, 'textures', sub)
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

  [ "$fail" = 0 ] || { echo "  贴图校验未通过"; return 1; }
  echo "  贴图校验通过"
}

# ---- 2. 贴图预览 ----------------------------------------------------------
preview_textures() {
  hr; echo "[2/4] 生成贴图预览"
  local out="$PROJ_ROOT/textures_sheet.png"
  python3 "$PROJ_ROOT/tools/tex_preview.py" "$ASSETS/textures" "$out"
  echo "  预览: $out"
}

# ---- 3. 编译打包 ----------------------------------------------------------
build_jar() {
  hr; echo "[3/4] 编译并打包"
  cd "$MOD_DIR"
  ./gradlew jar --offline 2>&1 | tail -8
  local src="$MOD_DIR/build/libs/schematicannon-1.0.0.jar"
  [ -f "$src" ] || { echo "  没有产出 jar，检查上面的编译输出"; return 1; }
  cp "$src" "$JAR_OUT"
  local size
  size=$(stat -c '%s' "$JAR_OUT" 2>/dev/null || stat -f '%z' "$JAR_OUT")
  echo "  jar: $JAR_OUT  (${size} 字节)"
}

# ---- 4. 校验 jar 内容 -----------------------------------------------------
verify_jar() {
  hr; echo "[4/4] 校验 jar 内的贴图"
  local n
  n=$(unzip -l "$JAR_OUT" | grep -c "assets/schematicannon/textures/.*\.png" || true)
  echo "  jar 里共 $n 张贴图"
  # 逐张比对 jar 内与磁盘上的字节数，确认打进去的是最新素材
  local drift=0
  while IFS= read -r line; do
    local path size name disk
    path=$(echo "$line" | awk '{print $4}')
    size=$(echo "$line" | awk '{print $1}')
    name=${path#assets/schematicannon/textures/}
    disk="$ASSETS/textures/$name"
    if [ -f "$disk" ]; then
      local dsize
      dsize=$(stat -c '%s' "$disk" 2>/dev/null || stat -f '%z' "$disk")
      if [ "$size" != "$dsize" ]; then
        echo "  与磁盘不一致: $name (jar=$size disk=$dsize)"
        drift=1
      fi
    else
      echo "  jar 里有磁盘上已删除的贴图: $name"
      drift=1
    fi
  done < <(unzip -l "$JAR_OUT" | awk '/assets\/schematicannon\/textures\/.*\.png/ {print}')
  if [ "$drift" = 0 ]; then
    echo "  jar 内贴图与磁盘上的素材完全一致"
  else
    echo "  警告: 有贴图不一致，确认一下 gradle 是否真的重新打包了"
    return 1
  fi
}

# ---- main ----------------------------------------------------------------
echo "Schematicannon 打包"
echo "  项目: $PROJ_ROOT"
echo "  JDK : $JAVA_HOME"

if [ "$PREVIEW_ONLY" = 1 ]; then
  check_textures || true
  preview_textures
  exit 0
fi

if [ "$CHECK_ONLY" = 1 ]; then
  check_textures
  preview_textures
  exit 0
fi

check_textures
[ "$QUICK" = 1 ] || preview_textures
build_jar
verify_jar

hr; echo "完成"
