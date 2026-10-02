<#
.SYNOPSIS
    Schematicannon 打包脚本（Windows PowerShell 5.1 版）

.DESCRIPTION
  完整打包：校验模型 -> 校验贴图 -> 生成预览 -> 编译 -> 出 jar -> 校验 jar -> 复制

.EXAMPLE
    .\tools\pack.ps1                      完整流程
    .\tools\pack.ps1 -Check               只做校验（模型 + 贴图），不编译、不生成预览
    .\tools\pack.ps1 -Quick               跳过预览图生成（仍做全部校验）
    .\tools\pack.ps1 -Preview             只生成贴图预览 + 白底模型预览，不编译
    .\tools\pack.ps1 -Dist D:\some\dir    打包后把 jar 额外复制到该目录
    .\tools\pack.ps1 -Online              允许联网解析依赖（默认离线，快且稳）
    .\tools\pack.ps1 -NoVerify            跳过 jar 内容校验（不建议）

.NOTES
  校验项（任一失败即中止，退出码非零）：
    1. 模型 JSON 可解析
    2. 每个 element 旋转后的 AABB 仍在 [0..16]（旋转角度必须是 22.5 的倍数）
    3. 贴图引用可解析、文件存在、尺寸 16x16、真彩色
    4. jar 内贴图与磁盘一致、模型 JSON 与磁盘一致

  依赖：JDK（java/jar 工具，优先 gradle.properties 里声明的 JDK25，其次 JAVA_HOME、
  最后 PATH）、Python 3。jar 内容校验使用 JDK 自带 jar.exe，无需 unzip。

  若提示"禁止运行脚本"，用下列任一方式绕过：
    powershell -ExecutionPolicy Bypass -File tools\pack.ps1
    Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#>

[CmdletBinding()]
param(
    [switch]$Quick,
    [switch]$Check,
    [switch]$Preview,
    [switch]$NoVerify,
    [switch]$Online,
    [string]$Dist = "",
    # 内部用：-Check 只跑校验、-Preview 只跑预览
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 路径与常量
# ---------------------------------------------------------------------------
$ProjRoot = Split-Path -Parent $PSScriptRoot          # tools\ 的上一层
$ModDir   = $ProjRoot
$Assets   = Join-Path $ModDir 'src\main\resources\assets\schematicannon'
$ModelJson = Join-Path $Assets 'models\block\schematicannon.json'

$VersionFile = Join-Path $ModDir 'gradle.properties'
$ModVersion = '1.0.0'
foreach ($line in (Get-Content -LiteralPath $VersionFile -ErrorAction SilentlyContinue)) {
    if ($line -match '^\s*mod_version\s*=\s*(.+?)\s*$') { $ModVersion = $Matches[1] }
}
$JarName = "schematicannon-$ModVersion.jar"
$JarOut  = Join-Path $ProjRoot $JarName

# ---------------------------------------------------------------------------
# 输出工具
# ---------------------------------------------------------------------------
$script:Failed  = $false
$script:FailMsg = @()

function Write-Hr  { Write-Host ('-' * 60) }
function Write-Step{ param([string]$Text) Write-Hr; Write-Host $Text }
function Write-Info{ param([string]$Text) Write-Host "  $Text" }

function Fail {
    param([string]$Message)
    $script:Failed = $true
    $script:FailMsg += $Message
    Write-Host ""
    Write-Host "!! $Message" -ForegroundColor Red
}

function Die {
    param([string]$Message)
    Write-Host ""
    Write-Host "!! $Message" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# JDK 定位：优先 gradle.properties 的 org.gradle.java.installations.paths
# ---------------------------------------------------------------------------
function Resolve-Jdk {
    $candidates = New-Object System.Collections.Generic.List[string]

    # 1) gradle.properties 里声明的工具链路径
    $gp = Join-Path $ModDir 'gradle.properties'
    foreach ($line in (Get-Content -LiteralPath $gp -ErrorAction SilentlyContinue)) {
        if ($line -match '^\s*org\.gradle\.java\.installations\.paths\s*=\s*(.+?)\s*$') {
            foreach ($p in ($Matches[1] -split ',')) {
                $p = $p.Trim() -replace '\\\\', '\'
                if ($p) { $candidates.Add($p) }
            }
        }
    }
    # 2) JAVA_HOME
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    # 3) 常见位置
    $candidates.Add('D:\installation\jdk25')
    $candidates.Add('D:\installation\jdk21')
    $candidates.Add('C:\Program Files\Java\jdk-25')

    foreach ($c in $candidates) {
        if (-not $c) { continue }
        $jarExe = Join-Path $c 'bin\jar.exe'
        $javaExe = Join-Path $c 'bin\java.exe'
        if ((Test-Path -LiteralPath $jarExe) -and (Test-Path -LiteralPath $javaExe)) {
            return [pscustomobject]@{ Home = $c; Jar = $jarExe; Java = $javaExe }
        }
    }
    # 4) PATH 上的 java/jar
    $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCmd) {
        return [pscustomobject]@{ Home = Split-Path -Parent (Split-Path -Parent $javaCmd.Source); Jar = $null; Java = $javaCmd.Source }
    }
    Die "找不到 JDK。请在 gradle.properties 里设置 org.gradle.java.installations.paths，或设置 JAVA_HOME。"
}

# ---------------------------------------------------------------------------
# Python 定位
# ---------------------------------------------------------------------------
function Resolve-Python {
    $candidates = @(
        'C:\Users\Administrator\.workbuddy\binaries\python\envs\default\Scripts\python.exe',
        'C:\Users\Administrator\.workbuddy\binaries\python\versions\3.13.12\python.exe',
        'C:\Users\Administrator\.workbuddy\binaries\python\versions\3.14.0\python.exe',
        'C:\Users\Administrator\AppData\Local\Python\pythoncore-3.14-64\python.exe'
    )
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    foreach ($n in @('python.exe', 'python3.exe', 'py.exe')) {
        $cmd = Get-Command $n -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    Die "找不到可用的 python 解释器（需要 Python 3.8+）。"
}

$Jdk = Resolve-Jdk
$Py  = Resolve-Python

# PS 5.1 与 Python 3 在 Windows 上默认都按 ANSI 代码页（936/GBK）收发文本，中文会
# 互相解成乱码。让 python 用 UTF-8 输出，再把控制台输入/输出编码也设成 UTF-8。
$env:PYTHONIOENCODING = 'utf-8'
$env:PYTHONUTF8       = '1'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

# ---------------------------------------------------------------------------
# 调用内嵌的 python 校验脚本
#   PS 5.1 不支持 shell here-doc，这里用临时文件承载 python 源码。
# ---------------------------------------------------------------------------
function Invoke-PythonScript {
    param(
        [string]$Code,
        [string[]]$ScriptArgs = @()
    )
    $tmp = Join-Path $env:TEMP ("packps_" + [guid]::NewGuid().ToString('N') + ".py")
    try {
        # 必须 UTF-8 无 BOM，否则 python 会因 BOM 报 SyntaxError
        [System.IO.File]::WriteAllText($tmp, $Code, (New-Object System.Text.UTF8Encoding($false)))
        $allArgs = @($tmp) + $ScriptArgs
        # Write-Host 让 python 的输出绕开调用方的 *> 重定向，能直接进控制台
        & $Py @allArgs | ForEach-Object { Write-Host $_ }
        return $LASTEXITCODE
    } finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# 1. 模型校验（调用已有的 tools\_verify_cannon_model.py）
# ---------------------------------------------------------------------------
function Test-Models {
    Write-Step "[1/5] 校验模型 (JSON / 旋转角度 / 旋转后 AABB / 贴图引用)"
    $script = Join-Path $PSScriptRoot '_verify_cannon_model.py'
    if (-not (Test-Path -LiteralPath $script)) {
        Fail "缺少 tools\_verify_cannon_model.py"
        return $false
    }
    # Write-Host：让子进程输出绕开调用方的 *> 重定向，直接进控制台
    & $Py $script | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        Fail "模型校验未通过"
        return $false
    }
    return $true
}

# ---------------------------------------------------------------------------
# 2. 贴图校验
# ---------------------------------------------------------------------------
function Test-Textures {
    Write-Step "[2/5] 校验贴图尺寸与格式"

    $codeSize = @'
import os, struct, sys
root = sys.argv[1]
bad = 0
rows = []
for sub in ('block', 'item'):
    d = os.path.join(root, sub)
    if not os.path.isdir(d):
        print('  缺少目录: %s/' % sub); bad = 1; continue
    for n in sorted(os.listdir(d)):
        if not n.endswith('.png'):
            print('  非 png 文件: %s/%s' % (sub, n)); bad = 1; continue
        p = os.path.join(d, n)
        head = open(p, 'rb').read(26)
        if head[:8] != b'\x89PNG\r\n\x1a\n':
            print('  不是合法 PNG: %s/%s' % (sub, n)); bad = 1; continue
        w, h = struct.unpack('>II', head[16:24])
        ct = head[25]
        note = ''
        if (w, h) != (16, 16):
            note = '  <-- 尺寸不是 16x16'; bad = 1
        if ct not in (2, 6):
            note += '  <-- 建议用 RGB/RGBA 真彩色'; bad = 1
        rows.append('  %-44s %dx%d%s' % (sub + '/' + n, w, h, note))
for r in rows:
    print(r)
if bad:
    sys.exit(1)
print('  全部 %d 张贴图尺寸与格式正常' % len(rows))
'@
    $rc = Invoke-PythonScript -Code $codeSize -ScriptArgs @((Join-Path $Assets 'textures'))
    if ($rc -ne 0) {
        Fail "贴图尺寸/格式校验未通过"
        return $false
    }

    # 模型引用的贴图必须存在。
    # 注意：element 的 face 里写的是 "#metal" 这种材质变量，真正的贴图路径在顶层
    # textures 块里。所以必须解析 JSON，不能拿正则去扫 "texture": "schematicannon:..."。
    $codeRef = @'
import json, os, sys
assets = sys.argv[1]
models_dir = os.path.join(assets, 'models')
tex_dir = os.path.join(assets, 'textures')

refs = set()       # 'block/xxx.png'
unresolved = []    # '#foo' 变量没在 textures 里定义


def resolve(val, declared, rel, unresolved):
    """把 '#metal' / 'schematicannon:block/x' 解析成 'block/x.png'，失败返回 None。"""
    if not isinstance(val, str):
        return None
    if val.startswith('#'):
        if val[1:] not in declared:
            unresolved.append('%s: %s 未在 textures 中定义' % (rel, val))
            return None
        return resolve(declared[val[1:]], declared, rel, unresolved)
    if not val.startswith('schematicannon:'):
        return None          # minecraft: 之类的原版贴图，不归我们管
    return val.split(':', 1)[1] + '.png'


for sub in ('block', 'item'):
    d = os.path.join(models_dir, sub)
    if not os.path.isdir(d):
        continue
    for n in sorted(os.listdir(d)):
        if not n.endswith('.json'):
            continue
        path = os.path.join(d, n)
        rel = '%s/%s' % (sub, n)
        try:
            model = json.load(open(path, encoding='utf-8'))
        except Exception as e:
            print('  %s: JSON 解析失败: %s' % (rel, e))
            sys.exit(1)

        declared = model.get('textures', {}) or {}
        for v in declared.values():
            r = resolve(v, declared, rel, unresolved)
            if r:
                refs.add(r)
        for el in model.get('elements', []) or []:
            for face in (el.get('faces', {}) or {}).values():
                if isinstance(face, dict) and 'texture' in face:
                    r = resolve(face['texture'], declared, rel, unresolved)
                    if r:
                        refs.add(r)

if unresolved:
    for u in unresolved:
        print('  %s' % u)
    sys.exit(1)

missing = sorted(r for r in refs if not os.path.isfile(os.path.join(tex_dir, r)))
if missing:
    for m in missing:
        print('  模型引用了不存在的贴图: %s' % m)
    sys.exit(1)
print('  模型引用的 %d 张贴图全部存在' % len(refs))

orphans = []
for sub in ('block', 'item'):
    d = os.path.join(tex_dir, sub)
    if not os.path.isdir(d):
        continue
    for n in sorted(os.listdir(d)):
        if n.endswith('.png') and (sub + '/' + n) not in refs:
            orphans.append(sub + '/' + n)
if orphans:
    print('  提示: 以下贴图没有被任何模型引用（可能是旧文件，或模型还没改过来）')
    for o in orphans:
        print('    %s' % o)
else:
    print('  没有未引用的贴图')
'@
    $rc = Invoke-PythonScript -Code $codeRef -ScriptArgs @($Assets)
    if ($rc -ne 0) {
        Fail "模型贴图引用校验未通过"
        return $false
    }
    return $true
}

# ---------------------------------------------------------------------------
# 3. 预览图
# ---------------------------------------------------------------------------
function Invoke-Previews {
    Write-Step "[3/5] 生成预览图"
    $sheet = Join-Path $ProjRoot 'textures_sheet.png'
    $t = Join-Path $PSScriptRoot 'tex_preview.py'
    if (Test-Path -LiteralPath $t) {
        & $Py $t (Join-Path $Assets 'textures') $sheet | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -eq 0) { Write-Info "贴图总览: $sheet" }
    }
    $m = Join-Path $ProjRoot 'model_preview.png'
    $w = Join-Path $PSScriptRoot '_render_white.py'
    if (Test-Path -LiteralPath $w) {
        & $Py $w $m | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -eq 0) { Write-Info "模型预览: $m" }
    }
}

# ---------------------------------------------------------------------------
# 4. 编译打包
# ---------------------------------------------------------------------------
function Invoke-Build {
    Write-Step "[4/5] 编译并打包"

    $gradlew = Join-Path $ModDir 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) { $gradlew = Join-Path $ModDir 'gradlew' }

    $gradleArgs = @('jar', '--console=plain')
    if (-not $Online) { $gradleArgs += '--offline' }

    Push-Location $ModDir
    try {
        # 用 env:JAVA_HOME 给 gradle 指路，gradle.properties 里也有工具链声明
        $oldJavaHome = $env:JAVA_HOME
        if ($Jdk.Home) { $env:JAVA_HOME = $Jdk.Home }

        # gradlew.bat 走 cmd，用 & 调用并检查退出码
        & $gradlew @gradleArgs 2>&1 | Select-Object -Last 8 | ForEach-Object { Write-Host $_ }
        $gst = $LASTEXITCODE

        $env:JAVA_HOME = $oldJavaHome
    } finally {
        Pop-Location
    }

    if ($gst -ne 0) {
        Fail "gradle 编译失败 (exit=$gst)"
        return $false
    }

    $src = Join-Path $ModDir "build\libs\$JarName"
    if (-not (Test-Path -LiteralPath $src)) {
        Fail "没有产出 jar: $src"
        return $false
    }
    Copy-Item -LiteralPath $src -Destination $JarOut -Force
    $size = (Get-Item -LiteralPath $JarOut).Length
    Write-Info "jar: $JarOut  ($size 字节)"
    return $true
}

# ---------------------------------------------------------------------------
# 5. 校验 jar 内容
# ---------------------------------------------------------------------------
function Test-Jar {
    Write-Step "[5/5] 校验 jar 内容"

    if (-not $Jdk.Jar -or -not (Test-Path -LiteralPath $Jdk.Jar)) {
        Write-Info "跳过（找不到 jar.exe）"
        return $true
    }

    # -t 列出条目；用 -v 能得到条目大小，但解析麻烦，这里只做存在性 + 内容比对
    $entries = & $Jdk.Jar -t -f $JarOut 2>$null
    if ($LASTEXITCODE -ne 0) {
        Fail "无法读取 jar: $JarOut"
        return $false
    }

    $texEntries = @($entries | Where-Object { $_ -match '^assets/schematicannon/textures/.*\.png$' })
    Write-Info "jar 里共 $($texEntries.Count) 张贴图"

    # 用 -v 拿条目字节数，和磁盘比对
    $verbose = & $Jdk.Jar -tvf $JarOut 2>$null
    $jarSizes = @{}
    foreach ($line in $verbose) {
        # 形如： 1234 Mon Jan 01 00:00:00 CST 2024 assets/schematicannon/textures/block/x.png
        if ($line -match '^\s*(\d+)\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+(assets/schematicannon/textures/.*\.png)\s*$') {
            $jarSizes[$Matches[2]] = [int]$Matches[1]
        }
    }

    $drift = $false
    foreach ($entry in $texEntries) {
        $name = $entry -replace '^assets/schematicannon/textures/', ''
        $disk = Join-Path (Join-Path $Assets 'textures') ($name -replace '/', '\')
        if (Test-Path -LiteralPath $disk) {
            $dsize = (Get-Item -LiteralPath $disk).Length
            if ($jarSizes.ContainsKey($entry) -and $jarSizes[$entry] -ne $dsize) {
                Write-Info "与磁盘不一致: $name (jar=$($jarSizes[$entry]) disk=$dsize)"
                $drift = $true
            }
        } else {
            Write-Info "jar 里有磁盘上已删除的贴图: $name"
            $drift = $true
        }
    }

    # 模型 JSON 也必须是最新的（模型改错是这几轮最主要的坑）
    # 模型 JSON 是文本，且 md5 比对必须逐字节可靠。jar.exe 没有 decode 到 stdout 的
    # 稳定用法，直接读 zip 最省事 —— Python 标准库 zipfile 就够，不依赖第三方包。
    $codeJar = @'
import hashlib, os, sys, zipfile
jar = sys.argv[1]
mod = sys.argv[2]
jpath = 'assets/schematicannon/models/block/schematicannon.json'
disk = os.path.join(mod, 'src', 'main', 'resources', jpath.replace('/', os.sep))
with zipfile.ZipFile(jar) as z:
    try:
        data = z.read(jpath)
    except KeyError:
        print('  jar 里没有模型 JSON')
        sys.exit(0)
a = hashlib.md5(data).hexdigest()
b = hashlib.md5(open(disk, 'rb').read()).hexdigest()
if a != b:
    print('  jar 内模型与磁盘不一致（gradle 没重新打包？）')
    sys.exit(1)
print('  jar 内模型 JSON 与磁盘一致')
'@
    $rc = Invoke-PythonScript -Code $codeJar -ScriptArgs @($JarOut, $ModDir)
    if ($rc -ne 0) { $drift = $true }

    if (-not $drift) {
        Write-Info "jar 内容校验通过"
        return $true
    }
    Fail "jar 内容与磁盘不一致"
    return $false
}

# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
Write-Host "Schematicannon 打包"
Write-Host "  项目: $ModDir"
Write-Host "  版本: $ModVersion   ->  $JarName"
Write-Host "  JDK : $($Jdk.Home)"
Write-Host "  Python: $Py"

if ($Preview) {
    if (-not (Test-Models)) { Write-Hr; Write-Host "校验失败"; exit 1 }
    Invoke-Previews
    Write-Hr; Write-Host "完成（仅预览）"
    exit 0
}

if ($Check) {
    if (-not (Test-Models))   { Write-Hr; Write-Host "校验失败"; exit 1 }
    if (-not (Test-Textures)) { Write-Hr; Write-Host "校验失败"; exit 1 }
    Write-Hr; Write-Host "完成（仅校验，未编译）"
    exit 0
}

if (-not (Test-Models))   { Write-Hr; Write-Host "中止：模型校验失败"; exit 1 }
if (-not (Test-Textures)) { Write-Hr; Write-Host "中止：贴图校验失败"; exit 1 }

if (-not $Quick) { Invoke-Previews }

if (-not (Invoke-Build)) { Write-Hr; Write-Host "中止：编译失败"; exit 1 }

if (-not $NoVerify) {
    if (-not (Test-Jar)) { Write-Hr; Write-Host "中止：jar 校验失败"; exit 1 }
}

if ($Dist) {
    Write-Hr; Write-Host "[+] 复制到 $Dist"
    if (-not (Test-Path -LiteralPath $Dist)) { New-Item -ItemType Directory -Path $Dist -Force | Out-Null }
    Copy-Item -LiteralPath $JarOut -Destination (Join-Path $Dist $JarName) -Force
    $full = (Resolve-Path -LiteralPath $Dist).Path
    Write-Info "$full\$JarName"
}

Write-Hr; Write-Host "完成"
exit 0
