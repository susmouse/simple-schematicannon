#!/usr/bin/env python3
"""原理图 .nbt 诊断工具（只读）。

用 nbtlib 解析文件，逐项报告：
  * 压缩格式（gzip / zlib / 裸 NBT）
  * 顶层键与标签类型
  * blocks[i].pos / .state 的真实标签类型（这是最容易踩的坑）
  * size 的类型（TAG_List<TAG_Int> 还是 TAG_Int_Array）
  * palette 每项是否有 Name / Properties
  * 用我们的 Schematic.load 的逻辑模拟一遍，报告能读出多少个方块

用法：
    python tools/schematicdump.py <文件.nbt>
"""
import sys
import os

try:
    import nbtlib
    from nbtlib.tag import (
        Compound, List as NbtList, Int, IntArray, String, Byte, Short, Long,
    )
except ImportError:
    sys.exit("需要 nbtlib：pip install nbtlib")


TAG_NAMES = {
    Byte: 'TAG_Byte', Short: 'TAG_Short', Int: 'TAG_Int', Long: 'TAG_Long',
    IntArray: 'TAG_Int_Array', String: 'TAG_String', Compound: 'TAG_Compound',
    NbtList: 'TAG_List',
}


def tname(v):
    for cls, name in TAG_NAMES.items():
        if isinstance(v, cls):
            return name
    # Float / Double / LongArray / ByteArray 兜底
    return type(v).__name__


def describe(v, depth=0):
    """把一个标签渲染成 'TAG_X<元素类型>' 这种可读描述。"""
    if isinstance(v, NbtList):
        if len(v):
            return '%s<%s>' % (tname(v), tname(v[0]))
        return 'TAG_List<空>'
    return tname(v)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path = sys.argv[1]
    if not os.path.isfile(path):
        sys.exit('找不到文件: %s' % path)

    raw = open(path, 'rb').read()
    print('=' * 74)
    print('文件: %s' % path)
    print('  大小: %d 字节' % len(raw))
    sig = raw[:2]
    if sig == b'\x1f\x8b':
        print('  压缩: gzip (1f 8b)  <- Create / 原版结构方块都这样')
    elif sig == b'\x78':
        print('  压缩: zlib (%s)' % raw[:2].hex())
    else:
        print('  压缩: 无（裸 NBT，首字节 %s）' % raw[:1].hex())

    try:
        nbt = nbtlib.load(path, gzipped=None)
    except Exception as e:
        sys.exit('  解析失败: %s' % e)

    print()
    print('=' * 74)
    print('顶层键')
    print('=' * 74)
    for k in nbt.keys():
        v = nbt[k]
        extra = ''
        if isinstance(v, NbtList):
            extra = '  长度=%d' % len(v)
        print('  %-14s %s%s' % (k, describe(v), extra))

    # ---- size -------------------------------------------------------------
    print()
    print('=' * 74)
    print('size  ->  期望 TAG_Int_Array，本文件是 %s' % describe(nbt['size']))
    print('=' * 74)
    size = nbt['size']
    if isinstance(size, IntArray):
        vals = list(size)
        print('  OK   合法 IntArray，值 = %s' % vals)
        print('  ->  Schematic.readSize() 的 getIntArray("size") 分支可用')
    elif isinstance(size, NbtList):
        vals = [int(x) for x in size]
        print('  值 = %s' % vals)
        print('  ->  getIntArray("size") 返回空，会走 ListTag 分支（代码里有兜底）')
    else:
        vals = None
        print('  !! 既不是 IntArray 也不是 List，readSize() 会退化成 [0,0,0]')
        print('     -> 代码里有"从方块推算尺寸"的兜底，但前提是方块能读出来')

    # ---- blocks -----------------------------------------------------------
    blocks = nbt.get('blocks')
    print()
    print('=' * 74)
    print('blocks  ->  元素类型 %s，数量 %d' % (tname(blocks[0]) if len(blocks) else '?', len(blocks)))
    print('=' * 74)
    if len(blocks):
        b0 = blocks[0]
        print('  blocks[0] 的字段：')
        for k in b0.keys():
            print('    %-8s %-22s = %r' % (k, describe(b0[k]), b0[k] if not isinstance(b0[k], NbtList) else list(b0[k])))

        # ---- pos 的关键判断 ----
        pos = b0.get('pos')
        print()
        if isinstance(pos, IntArray):
            print('  pos: TAG_Int_Array  ->  两边都对')
        elif isinstance(pos, NbtList):
            print('  pos: TAG_List<%s>  ->  !! getIntArray("pos") 会返回空 !' % tname(pos[0]))
            print('        Java 侧 tag.getIntArray("pos").orElse(null) == null')
            print('        原代码直接 continue，于是载入方块数 = 0')
        else:
            print('  pos: %s  ->  非预期类型' % tname(pos))

        # ---- state 的关键判断 ----
        st = b0.get('state')
        print()
        if isinstance(st, Compound):
            print('  state: TAG_Compound  ->  内联完整方块状态（Create 风格）')
        elif isinstance(st, (Int, Short, Byte, Long)):
            print('  state: %s  ->  palette 索引（原版结构方块风格）' % tname(st))
            print('        Java 侧 tag.getCompound("state") 返回空，走 getIntOr 分支 —— 这条是对的')
        else:
            print('  state: %s  ->  非预期类型' % tname(st))

    # ---- palette ----------------------------------------------------------
    pal = nbt.get('palette')
    if pal is not None:
        print()
        print('=' * 74)
        print('palette  ->  元素类型 %s，数量 %d' % (tname(pal[0]) if len(pal) else '?', len(pal)))
        print('=' * 74)
        no_name = [i for i, p in enumerate(pal) if 'Name' not in p]
        with_props = sum(1 for p in pal if 'Properties' in p)
        print('  缺 Name 的项     : %s' % (no_name if no_name else '无'))
        print('  带 Properties 的项: %d / %d' % (with_props, len(pal)))
        if no_name:
            print('  !! 有项缺 Name，NbtUtils.readBlockState 会读不出 -> 该项被丢弃')
        else:
            print('  OK   每项都有 Name，readBlockState 可用')
        if len(pal):
            print()
            print('  前 5 项：')
            for i, p in enumerate(pal[:5]):
                props = dict(p['Properties']) if 'Properties' in p else {}
                print('    [%2d] %-32s %s' % (i, p.get('Name', '<?>'), props if props else ''))

    # ---- 模拟 Java 的加载逻辑 ---------------------------------------------
    print()
    print('=' * 74)
    print('模拟 Schematic.load() —— 用当前代码的逻辑逐条判定')
    print('=' * 74)
    n_pos_ok = 0
    n_state_ok = 0
    n_ok = 0
    for b in blocks:
        pos = b.get('pos')
        if isinstance(pos, IntArray) and len(pos) >= 3:
            n_pos_ok += 1
        else:
            continue                                   # 原代码在这里 continue
        st = b.get('state')
        if isinstance(st, Compound):
            n_state_ok += 1
        elif isinstance(st, (Int, Short, Byte, Long)) and pal is not None:
            idx = int(st)
            if 0 <= idx < len(pal):
                n_state_ok += 1
        n_ok += 1

    print('  能通过 pos 检查的方块数  : %d / %d' % (n_pos_ok, len(blocks)))
    print('  其中 state 也能解析的    : %d / %d' % (n_state_ok, len(blocks)))
    print('  最终载入方块数           : %d' % n_ok)
    print()
    if n_ok == 0:
        print('  >>> 与"显示载入方块 0"相符。')
        if n_pos_ok == 0:
            print('  >>> 根因：blocks[].pos 是 TAG_List<TAG_Int>，不是 TAG_Int_Array。')
    elif n_ok < len(blocks):
        print('  >>> 部分方块被丢弃，检查上面的 pos/state 类型报告。')
    else:
        print('  >>> 全部方块都能读出，Java 侧应当正常。')


if __name__ == '__main__':
    main()
