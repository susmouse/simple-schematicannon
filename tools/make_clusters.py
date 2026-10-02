"""Split the port's still-broken upstream files into disjoint work clusters."""

import os

ROOT = r"D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys/schematicannon"

CLUSTERS = {
    "render": """
foundation/render/RenderTypes.java
foundation/render/MutableTemplateMesh.java
foundation/render/TemplateMesh.java
foundation/render/ShadowRenderHelper.java
foundation/render/BlockEntityRenderHelper.java
foundation/render/PlayerSkyhookRenderer.java
foundation/render/SpecialModels.java
foundation/model/BakedModelWrapperWithData.java
foundation/model/BakedQuadHelper.java
foundation/model/ModelSwapper.java
foundation/block/render/CustomBlockModels.java
foundation/block/render/MultiPosDestructionHandler.java
foundation/block/render/BlockDestructionProgressExtension.java
foundation/item/render/CustomItemModels.java
foundation/item/render/CustomRenderedItemModel.java
foundation/item/render/CustomRenderedItemModelRenderer.java
foundation/item/render/CustomRenderedItems.java
foundation/item/render/PartialItemModelRenderer.java
foundation/item/render/SimpleCustomRenderer.java
foundation/virtualWorld/VirtualChunk.java
foundation/virtualWorld/VirtualChunkSection.java
foundation/virtualWorld/VirtualChunkSource.java
foundation/virtualWorld/VirtualLevelEntityGetter.java
foundation/virtualWorld/VirtualRenderWorld.java
foundation/particle/AirParticle.java
foundation/particle/AirParticleData.java
foundation/particle/ICustomParticleData.java
foundation/particle/ICustomParticleDataWithSprite.java
content/schematics/client/SchematicRenderer.java
content/schematics/SchematicInstances.java
content/schematics/cannon/SchematicannonRenderer.java
content/schematics/StructureTransform.java
foundation/blockEntity/renderer/SafeBlockEntityRenderer.java
foundation/blockEntity/renderer/ColoredOverlayBlockEntityRenderer.java
foundation/mixin/accessor/BuiltInRegistriesAccessor.java
foundation/mixin/accessor/FluidInteractionRegistryAccessor.java
foundation/mixin/accessor/HumanoidArmorLayerAccessor.java
foundation/mixin/accessor/ItemFrameAccessor.java
foundation/mixin/accessor/LevelRendererAccessor.java
foundation/mixin/accessor/StateHolderAccessor.java
foundation/mixin/client/BlockDestructionProgressMixin.java
foundation/mixin/client/LevelRendererMixin.java
foundation/mixin/client/PlayerRendererMixin.java
foundation/mixin/SchematicannonMixinPlugin.java
foundation/block/render/CustomBlockModels.java
""",
    "gui": """
content/equipment/clipboard/ClipboardScreen.java
content/equipment/clipboard/ClipboardOverrides.java
content/equipment/clipboard/ClipboardValueSettingsHandler.java
content/schematics/cannon/SchematicannonScreen.java
content/schematics/table/SchematicTableScreen.java
content/schematics/client/SchematicEditScreen.java
content/schematics/client/SchematicPromptScreen.java
content/schematics/client/ToolSelectionScreen.java
content/schematics/client/SchematicHotbarSlotOverlay.java
content/schematics/client/tools/DeployTool.java
content/schematics/client/tools/FlipTool.java
content/schematics/client/tools/ISchematicTool.java
content/schematics/client/tools/MoveTool.java
content/schematics/client/tools/MoveVerticalTool.java
content/schematics/client/tools/PlaceTool.java
content/schematics/client/tools/PlacementToolBase.java
content/schematics/client/tools/RotateTool.java
content/schematics/client/tools/SchematicToolBase.java
content/schematics/client/tools/ToolType.java
foundation/gui/RemovedGuiUtils.java
foundation/gui/ScreenWithStencils.java
foundation/gui/CustomLightingSettings.java
foundation/gui/AllGuiTextures.java
foundation/gui/AllIcons.java
foundation/gui/ModularGuiLine.java
foundation/gui/ModularGuiLineBuilder.java
foundation/gui/menu/AbstractSimiContainerScreen.java
foundation/gui/widget/IconButton.java
foundation/gui/widget/Indicator.java
foundation/gui/widget/Label.java
foundation/gui/widget/ScrollInput.java
foundation/gui/widget/SelectionScrollInput.java
foundation/gui/widget/TooltipArea.java
foundation/blockEntity/behaviour/ValueBox.java
foundation/blockEntity/behaviour/ValueBoxTransform.java
foundation/blockEntity/behaviour/CenteredSideValueBoxTransform.java
foundation/blockEntity/behaviour/ValueSettingsBoard.java
foundation/blockEntity/behaviour/ValueSettingsFormatter.java
foundation/blockEntity/behaviour/ValueSettingsInputHandler.java
foundation/blockEntity/behaviour/scrollValue/ScrollValueBehaviour.java
foundation/blockEntity/behaviour/scrollValue/ScrollOptionBehaviour.java
foundation/blockEntity/behaviour/scrollValue/BulkScrollValueBehaviour.java
foundation/blockEntity/behaviour/scrollValue/ScrollValueRenderer.java
foundation/blockEntity/behaviour/scrollValue/ScrollValueHandler.java
foundation/blockEntity/behaviour/scrollValue/INamedIconOptions.java
""",
    "data": """
AllSoundEvents.java
AllKeys.java
AllTags.java
AllBlocks.java
AllItems.java
AllBlockEntityTypes.java
AllMenuTypes.java
AllShapes.java
AllSpecialTextures.java
AllParticleTypes.java
AllStructureProcessorTypes.java
AllSchematicStateFilters.java
foundation/data/SchematicannonRegistrate.java
foundation/data/CreateBlockEntityBuilder.java
foundation/data/CreateEntityBuilder.java
foundation/data/ModelGen.java
foundation/data/TagGen.java
foundation/data/AssetLookup.java
foundation/pack/DynamicPack.java
foundation/pack/DynamicPackSource.java
foundation/events/CommonEvents.java
foundation/events/ClientEvents.java
foundation/events/InputEvents.java
foundation/utility/CreateLang.java
foundation/utility/CreatePaths.java
foundation/utility/FilesHelper.java
foundation/utility/AdventureUtil.java
foundation/utility/TickBasedCache.java
foundation/utility/IInteractionChecker.java
foundation/utility/RaycastHelper.java
content/SharedProperties.java
api/registry/CreateRegistries.java
api/registry/SimpleRegistry.java
api/registry/registrate/SimpleBuilder.java
api/schematic/state/SchematicStateFilter.java
api/schematic/state/SchematicStateFilterRegistry.java
api/schematic/requirement/SchematicRequirementRegistries.java
impl/registrate/CreateRegistrateRegistrationCallbackImpl.java
impl/registry/MappedRegistryWithFreezeCallback.java
impl/registry/SimpleRegistryImpl.java
impl/registry/TagProviderImpl.java
infrastructure/RemapHelper.java
foundation/ClientResourceReloadListener.java
""",
    "network": """
AllPackets.java
foundation/networking/SimplePacketBase.java
foundation/networking/ISyncPersistentData.java
foundation/networking/BlockEntityConfigurationPacket.java
foundation/networking/BlockEntityDataPacket.java
foundation/blockEntity/RemoveBlockEntityPacket.java
foundation/blockEntity/SyncedBlockEntity.java
foundation/blockEntity/SmartBlockEntity.java
foundation/blockEntity/SmartBlockEntityTicker.java
foundation/blockEntity/CachedRenderBBBlockEntity.java
foundation/gui/menu/MenuBase.java
foundation/gui/menu/GhostItemMenu.java
foundation/gui/menu/GhostItemSubmitPacket.java
foundation/gui/menu/ClearMenuPacket.java
foundation/gui/menu/IClearableMenu.java
foundation/utility/ServerSpeedProvider.java
content/schematics/cannon/SchematicannonMenu.java
content/schematics/cannon/ConfigureSchematicannonPacket.java
content/schematics/cannon/SchematicannonInventory.java
content/schematics/cannon/SchematicannonBlock.java
content/schematics/table/SchematicTableMenu.java
content/schematics/table/SchematicTableBlock.java
content/schematics/packet/InstantSchematicPacket.java
content/schematics/packet/SchematicPlacePacket.java
content/schematics/packet/SchematicSyncPacket.java
content/schematics/packet/SchematicUploadPacket.java
content/equipment/blueprint/BlueprintAssignCompleteRecipePacket.java
content/equipment/clipboard/ClipboardEditPacket.java
content/equipment/clipboard/ClipboardCloneable.java
foundation/item/ItemHandlerWrapper.java
""",
}

HEADER = """# Port cluster: {name}

Files assigned to this cluster (paths are relative to
`D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys/schematicannon/`):

```
{files}```
"""


def main():
    out_dir = r"D:/Projects/minecraftMods/_tools/clusters"
    os.makedirs(out_dir, exist_ok=True)
    all_assigned = {}
    for name, blob in CLUSTERS.items():
        files = [l.strip() for l in blob.strip().splitlines() if l.strip()]
        files = sorted(set(files))
        for f in files:
            full = os.path.join(ROOT, f.replace("/", os.sep))
            if not os.path.exists(full):
                print(f"!! missing in {name}: {f}")
            if f in all_assigned:
                print(f"!! duplicate {f}: {name} and {all_assigned[f]}")
            all_assigned[f] = name
        with open(os.path.join(out_dir, f"{name}.txt"), "w", encoding="utf-8") as fh:
            fh.write(HEADER.format(name=name, files="\n".join(files) + "\n"))
        print(f"{name}: {len(files)} files")
    print(f"total assigned: {len(all_assigned)}")


if __name__ == "__main__":
    main()
