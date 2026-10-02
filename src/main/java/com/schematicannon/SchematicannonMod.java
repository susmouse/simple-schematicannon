package com.schematicannon;

import com.mojang.logging.LogUtils;
import com.schematicannon.network.ModNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(SchematicannonMod.MODID)
public class SchematicannonMod {

    public static final String MODID = "schematicannon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SchematicannonMod(IEventBus modBus, ModContainer container) {
        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.BLOCK_ENTITY_TYPES.register(modBus);
        ModRegistry.MENU_TYPES.register(modBus);
        ModRegistry.CREATIVE_TABS.register(modBus);

        modBus.addListener(ModNetwork::register);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(com.schematicannon.client.ClientHooks::registerScreens);
            // Game bus listeners live behind a dist guard as well: the classes they touch only
            // exist on the client, and a dedicated server must never load them.
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                    .addListener(com.schematicannon.client.ClientHooks::onSubmitGeometry);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                    .addListener(com.schematicannon.client.ClientHooks::onClientTick);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                    .addListener(com.schematicannon.client.ClientHooks::onLoggedOut);
        }
    }
}
