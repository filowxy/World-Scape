package com.worldscape;

import com.worldscape.config.ConfigManager;
import com.worldscape.debug.TerrainDebugSystem;
import com.worldscape.export.WorldSaveDataExporter;
import com.worldscape.generator.LandscapeChunkGenerator;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.TerrainTypeReloadListener;
import com.worldscape.voronoi.WorldScapeVoronoiSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
                LOGGER.info("[World Scape] Landscape loaded successfully");
            }));
        }
        catch (Exception e) {
            LOGGER.error("[World Scape] landscape load fail: {}", (Object)e.getMessage());
            throw new RuntimeException("[World Scape] Fatal: Failed to register LandscapeChunkGenerator", e);
        }
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);

        // Register config spec so NeoForge generates the config file and fires load/reload events
        ModList.get().getModContainerById(MOD_ID).ifPresent(container ->
            container.registerConfig(ModConfig.Type.COMMON, ConfigManager.SPEC));

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        // Register world save data exporter for terrain diagnostics (private tool, not for release).
        // 注册世界存档数据导出器用于地形诊断（私有工具，不公开发布）。
        NeoForge.EVENT_BUS.addListener(WorldSaveDataExporter::onWorldSave);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Load tunable tier height ranges from JSON config (one-time, on main thread).
        // Constants in WorldScapeConstants serve as fallback if the file is absent/malformed.
        // 一次性从 JSON 配置加载可调层级高度范围（主线程执行）；
        // 文件缺失或格式错误时以 WorldScapeConstants 中的常量为 fallback。
        event.enqueueWork(MacroVoronoiSystem::loadTierHeightConfig);
        // Load default terrain function definitions early so tests and offline tools can
        // resolve function signatures before the server starts. Full datapack/config overrides
        // are still loaded later via loadAll() in onServerStarting().
        // 提前加载默认地形函数定义，使测试和离线工具在服务器启动前即可解析函数签名。
        // 完整的数据包/配置覆盖仍由 onServerStarting() 中的 loadAll() 稍后加载。
        TerrainTypeReloadListener.loadDefaults();
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

