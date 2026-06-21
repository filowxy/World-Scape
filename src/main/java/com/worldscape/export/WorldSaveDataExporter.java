package com.worldscape.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.worldscape.WorldScape;
import com.worldscape.generator.FallbackSurfaceAdapter;
import com.worldscape.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.level.biome.Biome;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatically exports terrain data on world save for diagnostic analysis.
 * Collects all data synchronously during the save event, then writes files
 * asynchronously to avoid blocking the save thread.
 * This tool is PRIVATE — not for public release or distribution.
 * 在世界保存时自动导出地形数据用于诊断分析。
 * 在保存事件期间同步收集所有数据，然后异步写入文件以避免阻塞保存线程。
 * 此工具为私有工具 —— 不公开发布或分发。
 */
public class WorldSaveDataExporter {

    private static final Gson GSON = new GsonBuilder().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // Prevent concurrent exports — only one export at a time.
    // 防止并发导出 —— 同一时间仅允许一个导出任务。
    private static final AtomicBoolean exportInProgress = new AtomicBoolean(false);

    /**
     * Handles world save events. Collects all terrain data synchronously (in the
     * event thread) to ensure data consistency, then delegates file writing to an
     * async thread. This prevents issues where the main thread finishes and the
     * ServerLevel is unloaded before the async export completes.
     * 处理世界保存事件。在事件线程中同步收集所有地形数据以确保数据一致性，
     * 然后将文件写入委托给异步线程。这可以防止主线程先于异步导出完成而导致
     * ServerLevel 被卸载的问题。
     */
    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        // Only process server-side level saves.
        // 仅处理服务端世界保存。
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Prevent concurrent export tasks.
        // 防止并发导出任务。
        if (!exportInProgress.compareAndSet(false, true)) {
            WorldScape.LOGGER.info("[WorldScape] [Export] Previous export still in progress, skipping this save.");
            return;
        }

        try {
            List<ServerPlayer> players = serverLevel.players();
            if (players.isEmpty()) {
                WorldScape.LOGGER.info("[WorldScape] [Export] No players online, skipping export.");
                return;
            }

            long worldSeed = serverLevel.getSeed();
            int seaLevel = serverLevel.getSeaLevel();
            String worldName = getWorldName(serverLevel);
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

            // Create export directory.
            // 创建导出目录。
            Path baseDir = Paths.get(WorldScapeConstants.EXPORT_DIR_NAME, worldName, timestamp);
            Files.createDirectories(baseDir);

            // Phase 1: Collect all data synchronously (in event thread)
            // to avoid ServerLevel unloading before export completes.
            // 阶段1：在事件线程中同步收集所有数据，避免 ServerLevel 在导出完成前被卸载。
            List<ExportTask> tasks = new ArrayList<>();
            for (ServerPlayer player : players) {
                tasks.add(collectPlayerData(player, serverLevel, worldSeed, seaLevel, baseDir));
            }

            // Write manifest synchronously — lightweight.
            // 同步写入 manifest —— 轻量操作。
            writeManifest(baseDir, worldSeed, worldName, timestamp, players);

            // Phase 2: Write chunk data asynchronously.
            // Data is already collected in memory, so ServerLevel can safely unload.
            // 阶段2：异步写入区块数据。数据已收集到内存中，ServerLevel 可以安全卸载。
            final Path finalBaseDir = baseDir;
            CompletableFuture.runAsync(() -> {
                try {
                    for (ExportTask task : tasks) {
                        writeChunkFiles(finalBaseDir, task);
                    }
                    WorldScape.LOGGER.info("[WorldScape] [Export] Export completed: {} chunks to {}",
                        tasks.stream().mapToInt(t -> t.chunkData.size()).sum(), finalBaseDir);
                } catch (Exception e) {
                    WorldScape.LOGGER.error("[WorldScape] [Export] Async export failed: {}", e.getMessage(), e);
                } finally {
                    exportInProgress.set(false);
                }
            });

        } catch (Exception e) {
            WorldScape.LOGGER.error("[WorldScape] [Export] Failed to initiate export: {}", e.getMessage(), e);
            exportInProgress.set(false);
        }
    }

    /**
     * Collects all terrain data for a single player's surroundings.
     * Data is collected synchronously to ensure consistency.
     * 收集单个玩家周围的所有地形数据。同步收集以确保一致性。
     */
    private static ExportTask collectPlayerData(ServerPlayer player, ServerLevel level,
                                                  long worldSeed, int seaLevel, Path baseDir) {
        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        int radius = WorldScapeConstants.EXPORT_RADIUS_CHUNKS;

        // Create terrain calculation components from world seed.
        // 根据世界种子创建地形计算组件。
        RegionController controller = new RegionController(worldSeed, seaLevel);
        NoiseSet noiseSet = NoiseSet.getOrCreate(worldSeed);
        TerrainFieldSampler fieldSampler = TerrainFieldSampler.getOrCreate(worldSeed);

        ExportTask task = new ExportTask(player.getName().getString(), playerChunkX, playerChunkZ, radius);

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                List<String> lines = collectChunkData(chunk, level, controller, noiseSet, fieldSampler, seaLevel);
                if (!lines.isEmpty()) {
                    task.chunkData.put(new ChunkPos(cx, cz), lines);
                }
            }
        }

        return task;
    }

    /**
     * Collects terrain data for all 256 columns in a single chunk.
     * 收集单个区块内全部 256 列的地形数据。
     */
    private static List<String> collectChunkData(LevelChunk chunk, ServerLevel level,
                                                  RegionController controller, NoiseSet noiseSet,
                                                  TerrainFieldSampler fieldSampler, int seaLevel) {
        List<String> lines = new ArrayList<>();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = minX + x;
                int worldZ = minZ + z;

                String line = collectColumnData(worldX, worldZ, chunk, level,
                    controller, noiseSet, fieldSampler, seaLevel);
                if (line != null) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    /**
     * Collects terrain data for a single (x, z) column.
     * Returns a JSON string line, or null if the column is invalid.
     * 收集单个 (x, z) 列的地形数据。返回 JSON 字符串行，无效列返回 null。
     */
    private static String collectColumnData(int worldX, int worldZ, LevelChunk chunk,
                                             ServerLevel level, RegionController controller,
                                             NoiseSet noiseSet, TerrainFieldSampler fieldSampler,
                                             int seaLevel) {
        // Get surface height from chunk heightmap (WORLD_SURFACE for terrain top).
        // 从区块高度图获取地表高度（WORLD_SURFACE 为地形顶部）。
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX - chunk.getPos().getMinBlockX(),
            worldZ - chunk.getPos().getMinBlockZ());
        if (surfaceY <= level.getMinBuildHeight()) {
            return null; // Void or unloaded column.
        }

        // Get terrain blend and terrain type from World Scape systems.
        // 从 World Scape 系统获取地形混合和地形类型。
        RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
        TerrainType terrainType = TerrainCalculator.determineTerrainType(blend, fieldSampler, worldX, worldZ);
        double continuousHeight = TerrainCalculator.calculateFinalHeight(worldX, worldZ, blend, terrainType,
            noiseSet, fieldSampler);
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet,
            continuousHeight, seaLevel, blend);

        // Get surface block via FallbackSurfaceAdapter (matches what player sees).
        // 通过 FallbackSurfaceAdapter 获取表面方块（与玩家所见一致）。
        BlockState surfaceBlockState = FallbackSurfaceAdapter.determineSurfaceBlockByTerrainType(
            terrainType, surfaceY, seaLevel);

        // Get biome at surface position.
        // 获取地表位置的生物群系。
        BlockPos surfacePos = new BlockPos(worldX, surfaceY, worldZ);
        String biomeId = level.getBiome(surfacePos).unwrapKey()
            .map(key -> key.location().toString())
            .orElse("unknown");

        // Scan sub-surface blocks (y-1 through y-10).
        // 扫描次表层方块（y-1 到 y-10）。
        JsonArray subSurfaceBlocks = new JsonArray();
        for (int dy = 1; dy <= WorldScapeConstants.EXPORT_SUBSURFACE_DEPTH; dy++) {
            int y = surfaceY - dy;
            if (y < level.getMinBuildHeight()) break;
            BlockState state = chunk.getBlockState(new BlockPos(worldX, y, worldZ));
            subSurfaceBlocks.add(state.getBlock().builtInRegistryHolder().key().location().toString());
        }

        // Build JSON object.
        // 构建 JSON 对象。
        JsonObject obj = new JsonObject();
        obj.addProperty("x", worldX);
        obj.addProperty("z", worldZ);
        obj.addProperty("surfaceY", surfaceY);
        obj.addProperty("seaLevel", seaLevel);
        obj.addProperty("terrainType", terrainType.getId());
        obj.addProperty("macroElevationTier", blend.macroInfo.getElevationTier());
        obj.addProperty("blendWeight", blend.macroInfo.getBlendWeight());
        obj.addProperty("biome", biomeId);
        obj.addProperty("surfaceBlock",
            surfaceBlockState.getBlock().builtInRegistryHolder().key().location().toString());
        obj.add("subSurfaceBlocks", subSurfaceBlocks);
        obj.addProperty("continuousHeight", String.format("%.2f", continuousHeight));
        obj.addProperty("erosionIntensity", String.format("%.3f", erosionIntensity));
        obj.addProperty("dominantWeight", String.format("%.3f", blend.dominantWeight));

        return GSON.toJson(obj);
    }

    /**
     * Writes chunk JSONL files asynchronously from pre-collected data.
     * 从预收集的数据异步写入区块 JSONL 文件。
     */
    private static void writeChunkFiles(Path baseDir, ExportTask task) {
        for (Map.Entry<ChunkPos, List<String>> entry : task.chunkData.entrySet()) {
            ChunkPos pos = entry.getKey();
            List<String> lines = entry.getValue();

            Path chunkFile = baseDir.resolve(
                String.format("chunk_%d_%d.jsonl", pos.x, pos.z));
            try (BufferedWriter writer = Files.newBufferedWriter(chunkFile)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            } catch (IOException e) {
                WorldScape.LOGGER.error("[WorldScape] [Export] Failed to write chunk file {}: {}",
                    chunkFile, e.getMessage());
            }
        }
    }

    /**
     * Writes the export manifest file with metadata.
     * 写入导出的 manifest 文件，包含元数据。
     */
    private static void writeManifest(Path baseDir, long worldSeed, String worldName,
                                       String timestamp, List<ServerPlayer> players) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("worldSeed", worldSeed);
        manifest.addProperty("worldName", worldName);
        manifest.addProperty("exportTimestamp", timestamp);
        manifest.addProperty("exportRadiusChunks", WorldScapeConstants.EXPORT_RADIUS_CHUNKS);
        manifest.addProperty("subSurfaceDepth", WorldScapeConstants.EXPORT_SUBSURFACE_DEPTH);
        manifest.addProperty("modVersion", "1.0.0");

        JsonArray playerArray = new JsonArray();
        for (ServerPlayer player : players) {
            JsonObject p = new JsonObject();
            p.addProperty("name", player.getName().getString());
            p.addProperty("chunkX", player.chunkPosition().x);
            p.addProperty("chunkZ", player.chunkPosition().z);
            playerArray.add(p);
        }
        manifest.add("players", playerArray);

        Path manifestFile = baseDir.resolve("manifest.json");
        try (BufferedWriter writer = Files.newBufferedWriter(manifestFile)) {
            GSON.toJson(manifest, writer);
        } catch (IOException e) {
            WorldScape.LOGGER.error("[WorldScape] [Export] Failed to write manifest: {}", e.getMessage());
        }
    }

    /**
     * Extracts a clean world name from the ServerLevel's dimension location.
     * 从 ServerLevel 的维度位置提取干净的世界名称。
     */
    private static String getWorldName(ServerLevel level) {
        ResourceLocation dimLoc = level.dimension().location();
        // Use the save directory name as the world identifier.
        // 使用存档目录名作为世界标识符。
        String dimPath = dimLoc.getPath();
        // For overworld, try to get the actual save name from the server.
        // 对于主世界，尝试从服务器获取实际存档名称。
        try {
            String folderName = level.getServer().getWorldData().getLevelName();
            if (folderName != null && !folderName.isEmpty()) {
                return sanitizeFileName(folderName);
            }
        } catch (Exception e) {
            // Fallback to dimension name — log the failure per AGENTS.md §3.4.
            // Fixed: was empty catch block with `ignored` variable name.
            // 回退到维度名称 — 按 AGENTS.md §3.4 记录失败。
            // 修复：原为空 catch 块，变量名为 `ignored`。
            WorldScape.LOGGER.warn("[Export] Failed to get world save name, falling back to dimension name: {}", e.getMessage());
        }
        return sanitizeFileName("world_" + dimPath);
    }

    /**
     * Sanitizes a file name by removing unsafe characters.
     * 移除不安全字符以清理文件名。
     */
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]", "_");
    }

    /**
     * Holds pre-collected chunk data for one player for async writing.
     * 保存单个玩家的预收集区块数据，供异步写入使用。
     */
    private static class ExportTask {
        final String playerName;
        final int centerChunkX;
        final int centerChunkZ;
        final int radius;
        final Map<ChunkPos, List<String>> chunkData = new LinkedHashMap<>();

        ExportTask(String playerName, int centerChunkX, int centerChunkZ, int radius) {
            this.playerName = playerName;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.radius = radius;
        }
    }
}