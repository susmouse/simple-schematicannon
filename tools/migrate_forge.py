"""Mechanical Forge(1.20.1) -> NeoForge(26.1.2) source migration for the schematicannon port.

Only rewrites unambiguous import/identifier shapes. Everything context sensitive is left
untouched and reported so it can be fixed through compiler feedback.
"""

import os
import re
import sys
from collections import Counter

ROOT = r"D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys"

# (pattern, replacement, label)
RULES = [
    # --- dist markers -------------------------------------------------------
    (r"net\.minecraftforge\.api\.distmarker\.", "net.neoforged.api.distmarker.", "distmarker"),
    # --- event bus ----------------------------------------------------------
    (r"net\.minecraftforge\.eventbus\.api\.", "net.neoforged.bus.api.", "eventbus"),
    # --- fml ----------------------------------------------------------------
    (r"net\.minecraftforge\.fml\.loading\.", "net.neoforged.fml.loading.", "fml.loading"),
    (r"net\.minecraftforge\.fml\.", "net.neoforged.fml.", "fml"),
    # --- registries ---------------------------------------------------------
    (r"net\.minecraftforge\.registries\.ForgeRegistries", "net.minecraft.core.registries.BuiltInRegistries", "ForgeRegistries"),
    (r"BuiltInRegistries\.BLOCKS", "BuiltInRegistries.BLOCK", "ForgeRegistries.BLOCKS"),
    (r"BuiltInRegistries\.ITEMS", "BuiltInRegistries.ITEM", "ForgeRegistries.ITEMS"),
    (r"BuiltInRegistries\.BLOCK_ENTITY_TYPES", "BuiltInRegistries.BLOCK_ENTITY_TYPE", "ForgeRegistries.BET"),
    (r"BuiltInRegistries\.ENTITY_TYPES", "BuiltInRegistries.ENTITY_TYPE", "ForgeRegistries.ENTITY_TYPES"),
    (r"BuiltInRegistries\.MENU_TYPES", "BuiltInRegistries.MENU", "ForgeRegistries.MENU_TYPES"),
    (r"BuiltInRegistries\.PARTICLE_TYPES", "BuiltInRegistries.PARTICLE_TYPE", "ForgeRegistries.PARTICLES"),
    (r"BuiltInRegistries\.SOUND_EVENTS", "BuiltInRegistries.SOUND_EVENT", "ForgeRegistries.SOUND_EVENTS"),
    (r"BuiltInRegistries\.FLUIDS", "BuiltInRegistries.FLUID", "ForgeRegistries.FLUIDS"),
    (r"BuiltInRegistries\.CREATIVE_MODE_TABS", "BuiltInRegistries.CREATIVE_MODE_TAB", "ForgeRegistries.TABS"),
    (r"BuiltInRegistries\.STRUCTURE_PROCESSOR_TYPES", "BuiltInRegistries.STRUCTURE_PROCESSOR_TYPE", "ForgeRegistries.SPT"),
    (r"BuiltInRegistries\.RECIPE_TYPES", "BuiltInRegistries.RECIPE_TYPE", "ForgeRegistries.RECIPE_TYPES"),
    (r"net\.minecraftforge\.registries\.", "net.neoforged.neoforge.registries.", "registries"),
    # --- the rest of the Forge namespace ------------------------------------
    (r"net\.minecraftforge\.", "net.neoforged.neoforge.", "forge namespace"),
    # --- vanilla renames ----------------------------------------------------
    (r"net\.minecraft\.resources\.ResourceLocation", "net.minecraft.resources.Identifier", "ResourceLocation import"),
    (r"net\.minecraft\.network\.FriendlyByteBuf", "net.minecraft.network.RegistryFriendlyByteBuf", "FriendlyByteBuf"),
    (r"net\.minecraft\.world\.item\.ItemStack\.(isSame|isSameItemSameTags|isSameItemSameComponents)", r"ItemStack.\1", "unchanged"),
]

# new ResourceLocation(ns, path) / new ResourceLocation(str)
RE_RL_TWO = re.compile(r"new ResourceLocation\(\s*([^,()]+?)\s*,\s*([^,()]+?)\s*\)")
RE_RL_ONE = re.compile(r"new ResourceLocation\(\s*([^()]+?)\s*\)")


def migrate(text):
    counts = Counter()
    for pattern, repl, label in RULES:
        text, n = re.subn(pattern, repl, text)
        if n:
            counts[label] += n
    text, n = RE_RL_TWO.subn(r"Identifier.fromNamespaceAndPath(\1, \2)", text)
    if n:
        counts["new ResourceLocation(ns,path)"] += n
    text, n = RE_RL_ONE.subn(r"Identifier.parse(\1)", text)
    if n:
        counts["new ResourceLocation(str)"] += n
    return text, counts


def main():
    total = Counter()
    touched = 0
    for dirpath, _dirnames, filenames in os.walk(ROOT):
        for name in filenames:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as fh:
                original = fh.read()
            updated, counts = migrate(original)
            if updated != original:
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(updated)
                touched += 1
                total.update(counts)
    print(f"files rewritten: {touched}")
    for label, n in total.most_common():
        print(f"  {n:5d}  {label}")


if __name__ == "__main__":
    sys.exit(main())
