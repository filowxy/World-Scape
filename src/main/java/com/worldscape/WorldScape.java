package com.worldscape;

import com.worldscape.command.CommandManager;
import com.worldscape.debug.TerrainDebugSystem;
import com.worldscape.generator.LandscapeChunkGenerator;
import com.worldscape.voronoi.WorldScapeVoronoiSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value="worldscape")
public class WorldScape {
    public static final String MOD_ID = "worldscape";
    public static final Logger LOGGER = LoggerFactory.getLogger(WorldScape.class);

    public WorldScape(IEventBus modEventBus) {
        LOGGER.info("[World Scape] Mod initialized");
        try {
            modEventBus.addListener((RegisterEvent event) -> event.register(Registries.CHUNK_GENERATOR, helper -> {
                helper.register(ResourceLocation.fromNamespaceAndPath((String)MOD_ID, (String)"landscape"), LandscapeChunkGenerator.CODEC);
                LOGGER.info("[World Scape] Registered LandscapeChunkGenerator");
                LOGGER.info("[World Scape] landscape load correctly");
            }));
        }
        catch (Exception e) {
            LOGGER.error("[World Scape] landscape load fail: {}", (Object)e.getMessage());
        }
        CommandManager.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[World Scape] Common setup complete");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        WorldScapeVoronoiSystem.init(Minecraft.getInstance().gameDirectory);
        LOGGER.info("[World Scape] Voronoi visualization system initialized");
        LOGGER.info("[World Scape] Client setup complete");
        TerrainDebugSystem.init();
    }
}

