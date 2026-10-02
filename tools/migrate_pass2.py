"""Second mechanical pass for the schematicannon port.

Covers Minecraft 26.1 API shape changes that are globally unambiguous:

* CompoundTag / ValueInput getters moved from "return the value or a default" to
  Optional-returning plus explicit `*Or` variants.
* `Level#isClientSide` field became a method.
* NeoForge class relocations.

Only the `com/bikerboys` tree is touched - the vendored libraries are already written
against 26.1.
"""

import os
import re
import sys
from collections import Counter

ROOT = r"D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys"

# method name -> (replacement name, default argument expression or None)
OR_RULES = {
    "getString": ("getStringOr", '""'),
    "getInt": ("getIntOr", "0"),
    "getFloat": ("getFloatOr", "0f"),
    "getDouble": ("getDoubleOr", "0d"),
    "getBoolean": ("getBooleanOr", "false"),
    "getLong": ("getLongOr", "0L"),
    "getByte": ("getByteOr", "(byte) 0"),
    "getShort": ("getShortOr", "(short) 0"),
}
EMPTY_RULES = {
    "getCompound": "getCompoundOrEmpty",
    "getList": "getListOrEmpty",
}
ARRAY_RULES = {
    "getByteArray": "new byte[0]",
    "getIntArray": "new int[0]",
    "getLongArray": "new long[0]",
}

SIMPLE_RULES = [
    (re.compile(r"net\.neoforged\.fml\.common\.Mod\.EventBusSubscriber"),
     "net.neoforged.fml.common.EventBusSubscriber", "EventBusSubscriber"),
    (re.compile(r"net\.neoforged\.neoforge\.common\.MinecraftForge"),
     "net.neoforged.neoforge.common.NeoForge", "MinecraftForge->NeoForge"),
    (re.compile(r"net\.neoforged\.neoforge\.registries\.RegistryObject"),
     "net.minecraft.core.Holder", "RegistryObject->Holder(manual)"),
    (re.compile(r"\bisClientSide\b(?!\s*\()"), "isClientSide()", "isClientSide()"),
]


def split_args(argstr):
    """Split a top-level comma separated argument string."""
    if argstr.strip() == "":
        return []
    args, depth, current, in_str, quote = [], 0, [], False, ""
    for ch in argstr:
        if in_str:
            if ch == quote:
                in_str = False
            current.append(ch)
            continue
        if ch in "\"'":
            in_str, quote = True, ch
            current.append(ch)
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            args.append("".join(current))
            current = []
        else:
            current.append(ch)
    args.append("".join(current))
    return [a.strip() for a in args]


RE_CALL = re.compile(r"\.(getString|getInt|getFloat|getDouble|getBoolean|getLong|getByte|getShort|"
                     r"getCompound|getList|getByteArray|getIntArray|getLongArray|contains)\(")


def rewrite_calls(text, counts):
    out = []
    i = 0
    while True:
        m = RE_CALL.search(text, i)
        if not m:
            out.append(text[i:])
            break
        name = m.group(1)
        open_paren = m.end() - 1
        # find the matching close paren
        depth, j, in_str, quote = 0, open_paren, False, ""
        while j < len(text):
            ch = text[j]
            if in_str:
                if ch == quote and text[j - 1] != "\\":
                    in_str = False
            elif ch in "\"'":
                in_str, quote = True, ch
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        argstr = text[open_paren + 1:j]
        args = split_args(argstr)
        out.append(text[i:m.start()])

        if name in OR_RULES:
            if len(args) == 1:
                new = OR_RULES[name][0]
                out.append(f".{new}({args[0]}, {OR_RULES[name][1]})")
                counts[name] += 1
            else:  # already ported or unexpected arity - leave as is
                out.append(text[m.start():j + 1])
        elif name in EMPTY_RULES:
            if len(args) >= 1:
                out.append(f".{EMPTY_RULES[name]}({args[0]})")
                counts[name] += 1
            else:
                out.append(text[m.start():j + 1])
        elif name in ARRAY_RULES:
            if len(args) == 1:
                out.append(f".{name}({args[0]}).orElse({ARRAY_RULES[name]})")
                counts[name] += 1
            else:
                out.append(text[m.start():j + 1])
        elif name == "contains":
            if len(args) == 1:
                out.append(text[m.start():j + 1])
            elif len(args) >= 2:
                out.append(f".contains({args[0]})")
                counts["contains(k,t)"] += 1
            else:
                out.append(text[m.start():j + 1])
        i = j + 1
    return "".join(out)


def main():
    total = Counter()
    touched = 0
    for dirpath, _d, filenames in os.walk(ROOT):
        for fname in filenames:
            if not fname.endswith(".java"):
                continue
            path = os.path.join(dirpath, fname)
            with open(path, encoding="utf-8") as fh:
                original = fh.read()
            counts = Counter()
            text = rewrite_calls(original, counts)
            for pattern, repl, label in SIMPLE_RULES:
                text, n = pattern.subn(repl, text)
                if n:
                    counts[label] += n
            if text != original:
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(text)
                touched += 1
                total.update(counts)
    print(f"files rewritten: {touched}")
    for label, n in total.most_common():
        print(f"  {n:5d}  {label}")


if __name__ == "__main__":
    sys.exit(main())
