package com.worldscape.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.worldscape.WorldScape;
import com.worldscape.compat.c2me.C2MECompatibility;
import com.worldscape.debug.DebugPillarManager;
import com.worldscape.debug.TerrainDebugSystem;
import com.worldscape.debug.TerrainDebugTools;
import com.worldscape.terrain.ControlPointManager;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.TerrainVoronoiCache;
import com.worldscape.voronoi.VoronoiCamera;
import com.worldscape.voronoi.VoronoiControlPointManager;
import com.worldscape.voronoi.WorldScapeVoronoiSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid="worldscape")
public class CommandManager {
    private static final Path DEBUG_OUTPUT_DIR = new File("worldscape_debug").toPath();

    private static List<String> getTerrainSuggestions() {
        ArrayList<String> suggestions = new ArrayList<String>();
        for (TerrainType type : TerrainType.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath((String)"worldscape", (String)type.getId());
            suggestions.add(id.toString());
        }
        return suggestions;
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        List<String> terrainSuggestions = CommandManager.getTerrainSuggestions();
        event.getDispatcher().register((LiteralArgumentBuilder)Commands.literal((String)"locate").then(Commands.literal((String)"landscape").then(((RequiredArgumentBuilder)Commands.argument((String)"terrain", (ArgumentType)ResourceLocationArgument.id()).suggests((context, builder) -> {
            for (String suggestion : terrainSuggestions) {
                builder.suggest(suggestion);
            }
            return builder.buildFuture();
        }).requires(source -> ((CommandSourceStack)source).hasPermission(2))).executes(context -> {
            ResourceLocation location = ResourceLocationArgument.getId((CommandContext)context, (String)"terrain");
            TerrainType terrainType = null;
            for (TerrainType type : TerrainType.values()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath((String)"worldscape", (String)type.getId());
                if (!id.equals((Object)location)) continue;
                terrainType = type;
                break;
            }
            if (terrainType == null) {
                ((CommandSourceStack)context.getSource()).sendFailure((Component)Component.literal((String)("Unknown terrain type: " + String.valueOf(location))));
                return 0;
            }
            return CommandManager.locateLandscape((CommandSourceStack)context.getSource(), terrainType);
        }))));
        event.getDispatcher().register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"worldscape").then(Commands.literal((String)"seedinfo").executes(context -> CommandManager.showSeedInfo((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"debug").requires(source -> ((CommandSourceStack)source).hasPermission(2))).then(((LiteralArgumentBuilder)Commands.literal((String)"export_heightmap").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)100, (int)5000)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            return CommandManager.exportHeightmap((CommandSourceStack)context.getSource(), radius, 1);
        }))).executes(context -> CommandManager.exportHeightmap((CommandSourceStack)context.getSource(), 500, 1)))).then(((LiteralArgumentBuilder)Commands.literal((String)"export_voronoi").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)100, (int)5000)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            return CommandManager.exportVoronoi((CommandSourceStack)context.getSource(), radius, 1);
        }))).executes(context -> CommandManager.exportVoronoi((CommandSourceStack)context.getSource(), 500, 1)))).then(((LiteralArgumentBuilder)Commands.literal((String)"verify_consistency").then(Commands.argument((String)"size", (ArgumentType)IntegerArgumentType.integer((int)8, (int)64)).executes(context -> {
            int size = IntegerArgumentType.getInteger((CommandContext)context, (String)"size");
            return CommandManager.verifyConsistency((CommandSourceStack)context.getSource(), size, 16);
        }))).executes(context -> CommandManager.verifyConsistency((CommandSourceStack)context.getSource(), 32, 16)))).then(Commands.literal((String)"status").executes(context -> CommandManager.showDebugStatus((CommandSourceStack)context.getSource())))).then(Commands.literal((String)"c2me_report").executes(context -> CommandManager.c2meDiagnostic((CommandSourceStack)context.getSource())))).then(Commands.literal((String)"clear_cache").executes(context -> CommandManager.clearCache((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)Commands.literal((String)"export_enhanced").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)100, (int)5000)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            return CommandManager.exportEnhancedTerrainMap((CommandSourceStack)context.getSource(), radius, 1);
        }))).executes(context -> CommandManager.exportEnhancedTerrainMap((CommandSourceStack)context.getSource(), 500, 1)))).then(((LiteralArgumentBuilder)Commands.literal((String)"export_contour").then(((RequiredArgumentBuilder)Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)100, (int)5000)).then(Commands.argument((String)"interval", (ArgumentType)IntegerArgumentType.integer((int)10, (int)200)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            int interval = IntegerArgumentType.getInteger((CommandContext)context, (String)"interval");
            return CommandManager.exportContourMap((CommandSourceStack)context.getSource(), radius, 1, interval);
        }))).executes(context -> CommandManager.exportContourMap((CommandSourceStack)context.getSource(), IntegerArgumentType.getInteger((CommandContext)context, (String)"radius"), 1, 50)))).executes(context -> CommandManager.exportContourMap((CommandSourceStack)context.getSource(), 500, 1, 50)))).then(((LiteralArgumentBuilder)Commands.literal((String)"export_stats").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)100, (int)5000)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            return CommandManager.exportTerrainStats((CommandSourceStack)context.getSource(), radius, 16);
        }))).executes(context -> CommandManager.exportTerrainStats((CommandSourceStack)context.getSource(), 500, 16)))).then(((LiteralArgumentBuilder)Commands.literal((String)"scan_surface").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)4, (int)128)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            return CommandManager.scanSurfaceBlocks((CommandSourceStack)context.getSource(), radius);
        }))).executes(context -> CommandManager.scanSurfaceBlocks((CommandSourceStack)context.getSource(), 8)))).then(Commands.literal((String)"toggle_logging").then(Commands.argument((String)"enabled", (ArgumentType)BoolArgumentType.bool()).executes(context -> CommandManager.toggleLogging((CommandSourceStack)context.getSource(), BoolArgumentType.getBool((CommandContext)context, (String)"enabled")))))).then(Commands.literal((String)"toggle_pillars").then(Commands.argument((String)"enabled", (ArgumentType)BoolArgumentType.bool()).executes(context -> CommandManager.togglePillars((CommandSourceStack)context.getSource(), BoolArgumentType.getBool((CommandContext)context, (String)"enabled")))))).then(Commands.literal((String)"set_sample_rate").then(Commands.argument((String)"rate", (ArgumentType)IntegerArgumentType.integer((int)0, (int)500)).executes(context -> CommandManager.setSampleRate((CommandSourceStack)context.getSource(), IntegerArgumentType.getInteger((CommandContext)context, (String)"rate")))))).then(Commands.literal((String)"clear_pillars").executes(context -> CommandManager.clearPillars((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)Commands.literal((String)"clear_fluids").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)4, (int)128)).executes(context -> {
            int radius = IntegerArgumentType.getInteger((CommandContext)context, (String)"radius");
            return CommandManager.clearFluidsNearPlayer((CommandSourceStack)context.getSource(), radius);
        }))).executes(context -> CommandManager.clearFluidsNearPlayer((CommandSourceStack)context.getSource(), 16)))).then(Commands.literal((String)"region_overview").executes(context -> CommandManager.showRegionOverview((CommandSourceStack)context.getSource()))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"voronoi").executes(context -> CommandManager.voronoiStatus((CommandSourceStack)context.getSource()))).then(Commands.literal((String)"toggle").executes(context -> CommandManager.voronoiToggle((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)Commands.literal((String)"populate").then(Commands.argument((String)"radius", (ArgumentType)IntegerArgumentType.integer((int)100, (int)5000)).executes(context -> CommandManager.voronoiPopulate((CommandSourceStack)context.getSource(), IntegerArgumentType.getInteger((CommandContext)context, (String)"radius"))))).executes(context -> CommandManager.voronoiPopulate((CommandSourceStack)context.getSource(), 2048)))).then(Commands.literal((String)"save").executes(context -> CommandManager.voronoiSave((CommandSourceStack)context.getSource())))).then(Commands.literal((String)"load").executes(context -> CommandManager.voronoiLoad((CommandSourceStack)context.getSource())))).then(Commands.literal((String)"clear").executes(context -> CommandManager.voronoiClear((CommandSourceStack)context.getSource())))).then(Commands.literal((String)"center").executes(context -> CommandManager.voronoiCenter((CommandSourceStack)context.getSource())))));
    }

    private static int locateLandscape(CommandSourceStack source, TerrainType terrainType) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        RegionController regionController = new RegionController(seed, seaLevel);
        int maxRadius = 20000;
        int searchStep = 64;
        source.sendSuccess(() -> Component.literal((String)("Searching for " + terrainType.getId() + "...")), true);
        int startRegionX = (playerPos.getX() - maxRadius) / 256;
        int startRegionZ = (playerPos.getZ() - maxRadius) / 256;
        int endRegionX = (playerPos.getX() + maxRadius) / 256;
        int endRegionZ = (playerPos.getZ() + maxRadius) / 256;
        int closestX = 0;
        int closestZ = 0;
        int minDistance = Integer.MAX_VALUE;
        boolean found = false;
        for (int rx = startRegionX; rx <= endRegionX; ++rx) {
            for (int rz = startRegionZ; rz <= endRegionZ; ++rz) {
                int regionCenterX = rx * 256 + 128;
                int regionCenterZ = rz * 256 + 128;
                for (int sx = 0; sx < 256; sx += searchStep) {
                    for (int sz = 0; sz < 256; sz += searchStep) {
                        int distance;
                        int worldX = regionCenterX + sx - 128;
                        int worldZ = regionCenterZ + sz - 128;
                        RegionController.TerrainBlendResult blend = regionController.getTerrainBlend(worldX, worldZ);
                        TerrainType actualType = CommandManager.determineTerrainTypeForLocate(blend);
                        if (actualType != terrainType || (distance = (int)Math.sqrt(Math.pow(worldX - playerPos.getX(), 2.0) + Math.pow(worldZ - playerPos.getZ(), 2.0))) >= minDistance) continue;
                        minDistance = distance;
                        closestX = worldX;
                        closestZ = worldZ;
                        found = true;
                    }
                }
            }
        }
        if (found) {
            MutableComponent coordinateComponent = Component.literal((String)("[" + closestX + ", ~, " + closestZ + "]")).withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)65280)).withItalic(Boolean.valueOf(false)).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + closestX + " ~ " + closestZ)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((String)("Click to teleport to " + terrainType.getId() + " (" + minDistance + " blocks away)")))));
            MutableComponent message = Component.literal((String)("Found " + terrainType.getId() + " at ")).append((Component)coordinateComponent).append((Component)Component.literal((String)(" (" + minDistance + " blocks away)")));
            source.sendSuccess(() -> CommandManager.lambda$locateLandscape$39((Component)message), false);
            return 1;
        }
        source.sendFailure((Component)Component.literal((String)("Could not find " + terrainType.getId() + " within " + maxRadius + " blocks")));
        return 0;
    }

    private static TerrainType determineTerrainTypeForLocate(RegionController.TerrainBlendResult blend) {
        double dominantWeight = blend.dominantWeight;
        TerrainType dominantType = blend.dominantType;
        if (dominantWeight >= 0.4) {
            return dominantType;
        }
        int tier = blend.macroInfo.elevationTier;
        if (tier <= 0) {
            return TerrainType.TRENCH;
        }
        if (tier == 1) {
            return TerrainType.SEA_PLATEAU;
        }
        if (tier == 2) {
            return TerrainType.DELTA;
        }
        if (tier == 3) {
            return TerrainType.PLAINS;
        }
        if (tier == 4) {
            return TerrainType.HILLS;
        }
        if (tier == 5) {
            return TerrainType.PLATEAU;
        }
        return TerrainType.HIGH_MOUNTAINS;
    }

    private static int showSeedInfo(CommandSourceStack source) {
        long seed = source.getLevel().getSeed();
        source.sendSuccess(() -> Component.literal((String)("World seed: " + seed)), false);
        source.sendSuccess(() -> Component.literal((String)("Terrain generator seed: " + (seed ^ 0xDEADBEEFL))), false);
        return 1;
    }

    private static int exportHeightmap(CommandSourceStack source, int radiusBlocks, int pixelsPerBlock) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        int maxRadius = 5000;
        if (radiusBlocks > maxRadius) {
            source.sendFailure((Component)Component.literal((String)("Radius too large! Maximum is " + maxRadius + " blocks (creates " + maxRadius * 2 + "x" + maxRadius * 2 + " image).")));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int centerX = player.blockPosition().getX();
        int centerZ = player.blockPosition().getZ();
        try {
            if (!Files.exists(DEBUG_OUTPUT_DIR, new LinkOption[0])) {
                Files.createDirectories(DEBUG_OUTPUT_DIR, new FileAttribute[0]);
            }
            source.sendSuccess(() -> Component.literal((String)("Exporting heightmap (" + radiusBlocks * 2 + "x" + radiusBlocks * 2 + " blocks)...")), true);
            RegionController controller = new RegionController(seed, seaLevel);
            Path outputPath = DEBUG_OUTPUT_DIR.resolve("heightmap_" + centerX + "_" + centerZ + "_r" + radiusBlocks + ".png");
            TerrainDebugTools.exportHeightMapImage(controller, centerX, centerZ, radiusBlocks, pixelsPerBlock, outputPath);
            MutableComponent linkComponent = Component.literal((String)"[Click to open]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)49151)).withItalic(Boolean.valueOf(false)).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outputPath.toAbsolutePath().toString())).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((String)("Open: " + String.valueOf(outputPath.toAbsolutePath()))))));
            source.sendSuccess(() -> CommandManager.lambda$exportHeightmap$43((Component)linkComponent), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Failed to export heightmap: " + e.getMessage())));
            WorldScape.LOGGER.error("Failed to export heightmap", (Throwable)e);
            return 0;
        }
    }

    private static int exportVoronoi(CommandSourceStack source, int radiusBlocks, int pixelsPerBlock) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int centerX = player.blockPosition().getX();
        int centerZ = player.blockPosition().getZ();
        try {
            if (!Files.exists(DEBUG_OUTPUT_DIR, new LinkOption[0])) {
                Files.createDirectories(DEBUG_OUTPUT_DIR, new FileAttribute[0]);
            }
            source.sendSuccess(() -> Component.literal((String)"Exporting Voronoi regions..."), true);
            MacroVoronoiSystem macroSystem = new MacroVoronoiSystem(seed, seaLevel);
            Path outputPath = DEBUG_OUTPUT_DIR.resolve("voronoi_" + centerX + "_" + centerZ + "_r" + radiusBlocks + ".png");
            TerrainDebugTools.exportMacroVoronoiImage(macroSystem, centerX, centerZ, radiusBlocks, pixelsPerBlock, outputPath);
            MutableComponent linkComponent = Component.literal((String)"[Click to open]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)49151)).withItalic(Boolean.valueOf(false)).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outputPath.toAbsolutePath().toString())).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((String)("Open: " + String.valueOf(outputPath.toAbsolutePath()))))));
            source.sendSuccess(() -> CommandManager.lambda$exportVoronoi$45((Component)linkComponent), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Failed to export Voronoi: " + e.getMessage())));
            WorldScape.LOGGER.error("Failed to export Voronoi", (Throwable)e);
            return 0;
        }
    }

    private static int verifyConsistency(CommandSourceStack source, int size, int step) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int startX = player.blockPosition().getX() - size * step / 2;
        int startZ = player.blockPosition().getZ() - size * step / 2;
        try {
            source.sendSuccess(() -> Component.literal((String)("Running consistency check (" + size + "x" + size + ", step=" + step + ")...")), true);
            RegionController controller = new RegionController(seed, seaLevel);
            boolean consistent = TerrainDebugTools.verifyHeightConsistency(controller, startX, startZ, size, step);
            String singleMD5 = TerrainDebugTools.generateHeightMD5(controller, startX, startZ, size, step, false);
            String multiMD5 = TerrainDebugTools.generateHeightMD5(controller, startX, startZ, size, step, true);
            if (consistent) {
                source.sendSuccess(() -> Component.literal((String)"PASS: Single-threaded and multi-threaded MD5 match!").withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)65280)).withItalic(Boolean.valueOf(false))), false);
                source.sendSuccess(() -> Component.literal((String)("MD5: " + singleMD5)), false);
            } else {
                source.sendFailure((Component)Component.literal((String)"FAIL: MD5 mismatch detected!"));
                source.sendSuccess(() -> Component.literal((String)("Single-threaded: " + singleMD5)), false);
                source.sendSuccess(() -> Component.literal((String)("Multi-threaded: " + multiMD5)), false);
            }
            return consistent ? 1 : 0;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Consistency check failed: " + e.getMessage())));
            WorldScape.LOGGER.error("Consistency check failed", (Throwable)e);
            return 0;
        }
    }

    private static int showDebugStatus(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        long seed = level.getSeed();
        StringBuilder status = new StringBuilder();
        status.append("=== World Scape Debug Status ===\n");
        status.append("World Seed: ").append(seed).append("\n");
        status.append("Dimension: ").append(level.dimension().location()).append("\n");
        RegionController controller = new RegionController(seed, level.getSeaLevel());
        NoiseSet noiseSet = controller.getNoiseSet();
        status.append("NoiseSet seed: ").append(noiseSet.getWorldSeed()).append("\n");
        status.append("C2ME Present: ").append(C2MECompatibility.isC2MEPresent()).append("\n");
        status.append("C2ME Mode: ").append((Object)C2MECompatibility.getMode()).append("\n");
        status.append("\n");
        status.append("=== Terrain Debug System ===\n");
        status.append(TerrainDebugSystem.getStatusReport()).append("\n");
        status.append("Debug Blocks Placed: ").append(DebugPillarManager.getPlacedBlockCount()).append("\n");
        status.append("===============================");
        source.sendSuccess(() -> Component.literal((String)status.toString()), false);
        return 1;
    }

    private static int c2meDiagnostic(CommandSourceStack source) {
        String report = C2MECompatibility.generateDiagnosticReport();
        source.sendSuccess(() -> Component.literal((String)report), false);
        return 1;
    }

    private static int clearCache(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        TerrainVoronoiCache.clearDimensionCache((ResourceKey<Level>)level.dimension());
        source.sendSuccess(() -> Component.literal((String)("Terrain cache cleared for dimension: " + String.valueOf(level.dimension().location()))), false);
        return 1;
    }

    private static int exportEnhancedTerrainMap(CommandSourceStack source, int radiusBlocks, int pixelsPerBlock) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        if (!TerrainDebugSystem.isEnhancedHeightmapEnabled()) {
            source.sendFailure((Component)Component.literal((String)"Enhanced terrain map export is disabled. Use /worldscape debug enhanced_heightmap on to enable."));
            return 0;
        }
        int maxRadius = 2000;
        if (radiusBlocks > maxRadius) {
            source.sendFailure((Component)Component.literal((String)("Radius too large! Maximum is " + maxRadius + " blocks.")));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int centerX = player.blockPosition().getX();
        int centerZ = player.blockPosition().getZ();
        try {
            if (!Files.exists(DEBUG_OUTPUT_DIR, new LinkOption[0])) {
                Files.createDirectories(DEBUG_OUTPUT_DIR, new FileAttribute[0]);
            }
            source.sendSuccess(() -> Component.literal((String)("Exporting enhanced terrain map (" + radiusBlocks * 2 + "x" + radiusBlocks * 2 + " blocks)...")), true);
            RegionController controller = new RegionController(seed, seaLevel);
            Path outputPath = DEBUG_OUTPUT_DIR.resolve("terrain_enhanced_" + centerX + "_" + centerZ + "_r" + radiusBlocks + ".png");
            TerrainDebugTools.exportEnhancedTerrainMap(controller, centerX, centerZ, radiusBlocks, pixelsPerBlock, outputPath);
            MutableComponent linkComponent = Component.literal((String)"[Click to open]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)49151)).withItalic(Boolean.valueOf(false)).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outputPath.toAbsolutePath().toString())).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((String)("Open: " + String.valueOf(outputPath.toAbsolutePath()))))));
            source.sendSuccess(() -> CommandManager.lambda$exportEnhancedTerrainMap$55((Component)linkComponent), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Failed to export enhanced terrain map: " + e.getMessage())));
            WorldScape.LOGGER.error("Failed to export enhanced terrain map", (Throwable)e);
            return 0;
        }
    }

    private static int exportContourMap(CommandSourceStack source, int radiusBlocks, int pixelsPerBlock, int contourInterval) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int centerX = player.blockPosition().getX();
        int centerZ = player.blockPosition().getZ();
        try {
            if (!Files.exists(DEBUG_OUTPUT_DIR, new LinkOption[0])) {
                Files.createDirectories(DEBUG_OUTPUT_DIR, new FileAttribute[0]);
            }
            source.sendSuccess(() -> Component.literal((String)String.format("Exporting contour map (interval=%d)... This may take a moment.", contourInterval)), true);
            RegionController controller = new RegionController(seed, seaLevel);
            Path outputPath = DEBUG_OUTPUT_DIR.resolve("terrain_contour_" + centerX + "_" + centerZ + "_r" + radiusBlocks + "_i" + contourInterval + ".png");
            TerrainDebugTools.exportContourTerrainMap(controller, centerX, centerZ, radiusBlocks, pixelsPerBlock, contourInterval, outputPath);
            MutableComponent linkComponent = Component.literal((String)"[Click to open]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)49151)).withItalic(Boolean.valueOf(false)).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outputPath.toAbsolutePath().toString())).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((String)("Open: " + String.valueOf(outputPath.toAbsolutePath()))))));
            source.sendSuccess(() -> CommandManager.lambda$exportContourMap$57((Component)linkComponent), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Failed to export contour map: " + e.getMessage())));
            WorldScape.LOGGER.error("Failed to export contour map", (Throwable)e);
            return 0;
        }
    }

    private static int exportTerrainStats(CommandSourceStack source, int radiusBlocks, int pixelsPerBlock) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int centerX = player.blockPosition().getX();
        int centerZ = player.blockPosition().getZ();
        try {
            if (!Files.exists(DEBUG_OUTPUT_DIR, new LinkOption[0])) {
                Files.createDirectories(DEBUG_OUTPUT_DIR, new FileAttribute[0]);
            }
            source.sendSuccess(() -> Component.literal((String)"Exporting terrain statistics chart..."), true);
            RegionController controller = new RegionController(seed, seaLevel);
            Path outputPath = DEBUG_OUTPUT_DIR.resolve("terrain_stats_" + centerX + "_" + centerZ + "_r" + radiusBlocks + ".png");
            TerrainDebugTools.exportTerrainStatsChart(controller, centerX, centerZ, radiusBlocks, pixelsPerBlock, outputPath);
            MutableComponent linkComponent = Component.literal((String)"[Click to open]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)49151)).withItalic(Boolean.valueOf(false)).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outputPath.toAbsolutePath().toString())).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal((String)("Open: " + String.valueOf(outputPath.toAbsolutePath()))))));
            source.sendSuccess(() -> CommandManager.lambda$exportTerrainStats$59((Component)linkComponent), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Failed to export terrain stats: " + e.getMessage())));
            WorldScape.LOGGER.error("Failed to export terrain stats", (Throwable)e);
            return 0;
        }
    }

    private static int scanSurfaceBlocks(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int centerX = playerPos.getX();
        int centerZ = playerPos.getZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int halfSize = radius;
        int minBlockX = centerX - halfSize;
        int minBlockZ = centerZ - halfSize;
        int maxBlockX = centerX + halfSize;
        int maxBlockZ = centerZ + halfSize;
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        WorldScape.LOGGER.info("===== Surface Block Scan (player center: {}, {}) =====", (Object)centerX, (Object)centerZ);
        WorldScape.LOGGER.info("Area: {}x{} blocks (radius={})", new Object[]{radius * 2 + 1, radius * 2 + 1, radius});
        int scanned = 0;
        long scanStart = System.nanoTime();
        for (int x = minBlockX; x <= maxBlockX; ++x) {
            for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                ++scanned;
                int topY = maxY - 1;
                for (int y = maxY - 1; y > minY; --y) {
                    scanPos.set(x, y, z);
                    if (level.getBlockState((BlockPos)scanPos).isAir()) continue;
                    topY = y;
                    break;
                }
                String blockName = level.getBlockState((BlockPos)scanPos.set(x, topY, z)).getBlock().getName().getString();
                WorldScape.LOGGER.info("[SURFACE] ({}, {}, {}) block={}", new Object[]{x, topY, z, blockName});
            }
        }
        long scanTime = (System.nanoTime() - scanStart) / 1000000L;
        WorldScape.LOGGER.info("===== Scan Complete: {} blocks in {}ms =====", (Object)scanned, (Object)scanTime);
        int finalScanned = scanned;
        long finalScanTime = scanTime;
        source.sendSuccess(() -> Component.literal((String)String.format("Scanned %d surface blocks in %dms. See logs for details.", finalScanned, finalScanTime)), false);
        return 1;
    }

    private static int clearFluidsNearPlayer(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int centerX = playerPos.getX();
        int centerZ = playerPos.getZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int halfSize = radius;
        int minBlockX = centerX - halfSize;
        int minBlockZ = centerZ - halfSize;
        int maxBlockX = centerX + halfSize;
        int maxBlockZ = centerZ + halfSize;
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        int fluidsCleared = 0;
        int snowCleared = 0;
        int totalBlocks = (maxBlockX - minBlockX + 1) * (maxBlockZ - minBlockZ + 1);
        int progressStep = Math.max(1, totalBlocks / 10);
        source.sendSuccess(() -> Component.literal((String)("Clearing fluids in " + radius * 2 + "x" + radius * 2 + " area...")), false);
        int blockIndex = 0;
        for (int x = minBlockX; x <= maxBlockX; ++x) {
            for (int z = minBlockZ; z <= maxBlockZ; ++z) {
                ++blockIndex;
                for (int y = maxY - 1; y > minY; --y) {
                    scanPos.set(x, y, z);
                    BlockState state = level.getBlockState((BlockPos)scanPos);
                    if (state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA)) {
                        level.setBlock((BlockPos)scanPos, Blocks.AIR.defaultBlockState(), 3);
                        ++fluidsCleared;
                    }
                    if (!state.is(Blocks.SNOW)) continue;
                    level.setBlock((BlockPos)scanPos, Blocks.AIR.defaultBlockState(), 3);
                    ++snowCleared;
                }
                if (blockIndex % progressStep != 0 || !WorldScape.LOGGER.isDebugEnabled()) continue;
                WorldScape.LOGGER.debug("[World Scape] Fluid clearing progress: {}%", (Object)(blockIndex * 100 / totalBlocks));
            }
        }
        int finalFluidsCleared = fluidsCleared;
        int finalSnowCleared = snowCleared;
        source.sendSuccess(() -> Component.literal((String)String.format("Cleared %d fluid blocks and %d snow layers within radius %d", finalFluidsCleared, finalSnowCleared, radius)), false);
        return 1;
    }

    private static int toggleLogging(CommandSourceStack source, boolean enabled) {
        TerrainDebugSystem.setLoggingEnabled(enabled);
        source.sendSuccess(() -> Component.literal((String)String.format("Debug logging %s (sample rate: 1/%d)", enabled ? "enabled" : "disabled", TerrainDebugSystem.getChunkSampleRate())), false);
        return 1;
    }

    private static int togglePillars(CommandSourceStack source, boolean enabled) {
        TerrainDebugSystem.setPillarsEnabled(enabled);
        source.sendSuccess(() -> Component.literal((String)("Debug pillars " + (enabled ? "enabled" : "disabled"))), false);
        return 1;
    }

    private static int setSampleRate(CommandSourceStack source, int rate) {
        TerrainDebugSystem.setChunkSampleRate(rate);
        source.sendSuccess(() -> Component.literal((String)String.format("Chunk sample rate set to %s", rate == 0 ? "disabled" : "1/" + rate)), false);
        return 1;
    }

    private static int clearPillars(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        DebugPillarManager.clearAllPillars((Level)level);
        source.sendSuccess(() -> Component.literal((String)String.format("Cleared %d debug blocks", DebugPillarManager.getPlacedBlockCount())), false);
        return 1;
    }

    private static int showRegionOverview(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int worldX = player.blockPosition().getX();
        int worldZ = player.blockPosition().getZ();
        RegionController controller = new RegionController(seed, seaLevel);
        RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
        StringBuilder info = new StringBuilder();
        info.append("=== Terrain Frame Overview ===\n");
        info.append(String.format("Position: (%d, ~, %d)\n", worldX, worldZ));
        info.append(String.format("Elevation Tier: %d (base height: %.1f)\n", blend.macroInfo.elevationTier, blend.macroInfo.blendedBaseHeight));
        info.append(String.format("Dominant Type: %s (weight: %.2f)\n", blend.dominantType.getId(), blend.dominantWeight));
        info.append(String.format("Blended Height: %.1f\n", blend.blendedHeight));
        info.append(String.format("Tectonic: %s | Climate: %s\n", new Object[]{blend.macroInfo.tectonic, blend.macroInfo.climate}));
        info.append(String.format("Contributing Points: %d\n", blend.contributingPoints.size()));
        info.append(String.format("Macro Cell: (%d, %d) | Blend Weight: %.3f | Transition Width: %d\n", blend.macroInfo.primaryCellX, blend.macroInfo.primaryCellZ, blend.macroInfo.blendWeight, blend.macroInfo.transitionWidth));
        info.append("=============================");
        source.sendSuccess(() -> Component.literal((String)info.toString()), false);
        return 1;
    }

    private static int voronoiStatus(CommandSourceStack source) {
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        boolean enabled = WorldScapeVoronoiSystem.isEnabled();
        String status = String.format("\u00a7b\u00a7l=== World Scape Voronoi System ===\n\u00a77Enabled: \u00a7f%s\n\u00a77Control Points: \u00a7f%d\n\u00a77Selected: \u00a7f%d\n\u00a77Memory: \u00a7f%d KB\n\u00a77View Mode: \u00a7f%s\n\u00a77Zoom: \u00a7f%.2fx\n\u00a77Camera Position: \u00a7f(%.0f, %.0f)\n\u00a7b=============================", enabled ? "\u00a7aYes" : "\u00a7cNo", manager != null ? manager.getPointCount() : 0, manager != null ? manager.getSelectedIds().size() : 0, manager != null ? manager.estimateMemoryUsage() / 1024L : 0L, camera != null ? camera.getViewMode().getDisplayName() : "N/A", Float.valueOf(camera != null ? camera.getZoomLevel() : 0.0f), camera != null ? camera.getCameraX() : 0.0, camera != null ? camera.getCameraZ() : 0.0);
        source.sendSuccess(() -> Component.literal((String)status), false);
        return 1;
    }

    private static int voronoiToggle(CommandSourceStack source) {
        WorldScapeVoronoiSystem.toggle();
        boolean enabled = WorldScapeVoronoiSystem.isEnabled();
        source.sendSuccess(() -> Component.literal((String)(enabled ? "\u00a7aVoronoi overlay enabled" : "\u00a7cVoronoi overlay disabled")), false);
        return 1;
    }

    private static int voronoiPopulate(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getSeed();
        int seaLevel = level.getSeaLevel();
        int centerX = player.blockPosition().getX();
        int centerZ = player.blockPosition().getZ();
        try {
            MacroVoronoiSystem macroSystem = new MacroVoronoiSystem(seed, seaLevel);
            ControlPointManager controlPointManager = new ControlPointManager(seed, seaLevel);
            WorldScapeVoronoiSystem.getControlPointManager().importFromTerrainSystem(centerX, centerZ, radius, macroSystem, controlPointManager);
            int count = WorldScapeVoronoiSystem.getControlPointManager().getPointCount();
            source.sendSuccess(() -> Component.literal((String)String.format("\u00a7aPopulated %d control points from terrain system (radius: %d blocks)", count, radius)), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure((Component)Component.literal((String)("Failed to populate control points: " + e.getMessage())));
            WorldScape.LOGGER.error("Failed to populate Voronoi control points", (Throwable)e);
            return 0;
        }
    }

    private static int voronoiSave(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        boolean success = WorldScapeVoronoiSystem.save();
        int count = WorldScapeVoronoiSystem.getControlPointManager().getPointCount();
        source.sendSuccess(() -> Component.literal((String)(success ? String.format("\u00a7aSaved %d control points", count) : "\u00a7cFailed to save control points")), false);
        return success ? 1 : 0;
    }

    private static int voronoiLoad(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        WorldScapeVoronoiSystem.load();
        int count = WorldScapeVoronoiSystem.getControlPointManager().getPointCount();
        source.sendSuccess(() -> Component.literal((String)String.format("\u00a7aLoaded %d control points", count)), false);
        return 1;
    }

    private static int voronoiClear(CommandSourceStack source) {
        WorldScapeVoronoiSystem.clear();
        source.sendSuccess(() -> Component.literal((String)"\u00a7aAll control points cleared"), false);
        return 1;
    }

    private static int voronoiCenter(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure((Component)Component.literal((String)"This command requires a player"));
            return 0;
        }
        int px = player.blockPosition().getX();
        int pz = player.blockPosition().getZ();
        WorldScapeVoronoiSystem.getCamera().panTo(px, pz);
        source.sendSuccess(() -> Component.literal((String)String.format("\u00a7aCamera centered at player position (%d, %d)", px, pz)), false);
        return 1;
    }

    public static void register(IEventBus modEventBus) {
    }

    private static /* synthetic */ Component lambda$exportTerrainStats$59(Component linkComponent) {
        return Component.literal((String)"Terrain stats chart exported to: ").append(linkComponent);
    }

    private static /* synthetic */ Component lambda$exportContourMap$57(Component linkComponent) {
        return Component.literal((String)"Contour map exported to: ").append(linkComponent);
    }

    private static /* synthetic */ Component lambda$exportEnhancedTerrainMap$55(Component linkComponent) {
        return Component.literal((String)"Enhanced terrain map exported to: ").append(linkComponent);
    }

    private static /* synthetic */ Component lambda$exportVoronoi$45(Component linkComponent) {
        return Component.literal((String)"Voronoi regions exported to: ").append(linkComponent);
    }

    private static /* synthetic */ Component lambda$exportHeightmap$43(Component linkComponent) {
        return Component.literal((String)"Heightmap exported to: ").append(linkComponent);
    }

    private static /* synthetic */ Component lambda$locateLandscape$39(Component message) {
        return message;
    }
}

