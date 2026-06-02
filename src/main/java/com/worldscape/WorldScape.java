package com.worldscape;

import com.worldscape.command.CommandManager;
import com.worldscape.debug.TerrainDebugSystem;
import com.worldscape.generator.LandscapeChunkGenerator;
import com.worldscape.terrain.TerrainTypeReloadListener;
import com.worldscape.voronoi.WorldScapeVoronoiSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
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

        TerrainTypeReloadListener.loadAll();

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
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

    /**
     * Handle server starting event to reload terrain type config overrides.
     * 处理服务器启动事件以重新加载地形类型配置覆盖。
     * <p>
     * When the server starts, this reloads terrain type definitions from the
     * config directory to ensure any changes made between client and server
     * start are picked up.
     * 当服务器启动时，从配置目录重新加载地形类型定义，以确保在客户端和服务器
     * 启动之间所做的任何更改都能被应用。
     *
     * @param event the server starting event / 服务器启动事件
     */
    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[World Scape] Server starting - reloading terrain type configs...");
        // [World Scape] 服务器启动 - 重新加载地形类型配置...
        TerrainTypeReloadListener.loadAll();
    }
}

