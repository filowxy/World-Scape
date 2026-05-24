package com.worldscape.voronoi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldscape.WorldScape;
import com.worldscape.voronoi.VoronoiControlPoint;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VoronoiDataPersistence {
    private static final String FILE_NAME = "voronoi_points.json";
    private static final String BACKUP_SUFFIX = ".backup";

    public static boolean save(List<VoronoiControlPoint> points, File file) {
        File configFile = new File(file, FILE_NAME);
        File backupFile = new File(file, "voronoi_points.json.backup");
        File tempFile = new File(file, "voronoi_points.json.tmp");
        try {
            if (!file.exists() && !file.mkdirs()) {
                WorldScape.LOGGER.error("Failed to create Voronoi data directory: {}", (Object)file.getAbsolutePath());
                return false;
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", (Number)1);
            root.addProperty("count", (Number)points.size());
            JsonArray pointsArray = new JsonArray();
            for (VoronoiControlPoint point : points) {
                JsonObject pointObj = new JsonObject();
                pointObj.addProperty("id", point.getId());
                pointObj.addProperty("x", (Number)point.getX());
                pointObj.addProperty("z", (Number)point.getZ());
                pointObj.addProperty("color", (Number)point.getColor());
                pointObj.addProperty("size", (Number)Float.valueOf(point.getSize()));
                pointObj.addProperty("weight", (Number)Float.valueOf(point.getWeight()));
                pointObj.addProperty("label", point.getLabel());
                pointObj.addProperty("terrainType", point.getTerrainType());
                pointObj.addProperty("visible", Boolean.valueOf(point.isVisible()));
                pointsArray.add((JsonElement)pointObj);
            }
            root.add("points", (JsonElement)pointsArray);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 OutputStreamWriter writer = new OutputStreamWriter((OutputStream)fos, StandardCharsets.UTF_8);){
                gson.toJson((JsonElement)root, (Appendable)writer);
                writer.flush();
                fos.getFD().sync();
            }
            if (configFile.exists()) {
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                configFile.renameTo(backupFile);
            }
            if (!tempFile.renameTo(configFile)) {
                WorldScape.LOGGER.error("Failed to rename temp file to config file");
                return false;
            }
            WorldScape.LOGGER.info("Saved {} Voronoi control points to {}", (Object)points.size(), (Object)configFile.getAbsolutePath());
            return true;
        }
        catch (IOException e) {
            WorldScape.LOGGER.error("Failed to save Voronoi control points", (Throwable)e);
            if (!configFile.exists() && backupFile.exists()) {
                backupFile.renameTo(configFile);
            }
            return false;
        }
    }

    public static List<VoronoiControlPoint> load(File file) {
        ArrayList<VoronoiControlPoint> points;
        block7: {
            points = new ArrayList<VoronoiControlPoint>();
            File configFile = new File(file, FILE_NAME);
            if (!configFile.exists()) {
                WorldScape.LOGGER.info("No Voronoi control points file found at {}", (Object)configFile.getAbsolutePath());
                return points;
            }
            try {
                String json = VoronoiDataPersistence.readFileAsString(configFile);
                if (json == null || json.isEmpty()) {
                    return points;
                }
                JsonObject root = JsonParser.parseString((String)json).getAsJsonObject();
                int version = root.has("version") ? root.get("version").getAsInt() : 1;
                JsonArray pointsArray = root.getAsJsonArray("points");
                for (int i = 0; i < pointsArray.size(); ++i) {
                    try {
                        JsonObject pointObj = pointsArray.get(i).getAsJsonObject();
                        VoronoiControlPoint point = VoronoiDataPersistence.parsePoint(pointObj, version);
                        if (point == null) continue;
                        points.add(point);
                        continue;
                    }
                    catch (Exception e) {
                        WorldScape.LOGGER.warn("Failed to parse Voronoi control point at index {}, skipping", (Object)i, (Object)e);
                    }
                }
                WorldScape.LOGGER.info("Loaded {} Voronoi control points from {}", (Object)points.size(), (Object)configFile.getAbsolutePath());
            }
            catch (Exception e) {
                WorldScape.LOGGER.error("Failed to load Voronoi control points", (Throwable)e);
                File backupFile = new File(file, "voronoi_points.json.backup");
                if (!backupFile.exists()) break block7;
                WorldScape.LOGGER.info("Attempting to load from backup...");
                return VoronoiDataPersistence.loadFromBackup(backupFile);
            }
        }
        return points;
    }

    private static List<VoronoiControlPoint> loadFromBackup(File backupFile) {
        ArrayList<VoronoiControlPoint> points = new ArrayList<VoronoiControlPoint>();
        try {
            String json = VoronoiDataPersistence.readFileAsString(backupFile);
            if (json == null || json.isEmpty()) {
                return points;
            }
            JsonObject root = JsonParser.parseString((String)json).getAsJsonObject();
            JsonArray pointsArray = root.getAsJsonArray("points");
            for (int i = 0; i < pointsArray.size(); ++i) {
                JsonObject pointObj = pointsArray.get(i).getAsJsonObject();
                VoronoiControlPoint point = VoronoiDataPersistence.parsePoint(pointObj, 1);
                if (point == null) continue;
                points.add(point);
            }
            WorldScape.LOGGER.info("Loaded {} Voronoi control points from backup", (Object)points.size());
        }
        catch (Exception e) {
            WorldScape.LOGGER.error("Failed to load from backup", (Throwable)e);
        }
        return points;
    }

    private static VoronoiControlPoint parsePoint(JsonObject obj, int version) {
        try {
            String id = obj.get("id").getAsString();
            int x = obj.get("x").getAsInt();
            int z = obj.get("z").getAsInt();
            int color = obj.get("color").getAsInt();
            float size = obj.has("size") ? obj.get("size").getAsFloat() : 8.0f;
            float weight = obj.has("weight") ? obj.get("weight").getAsFloat() : 1.0f;
            String label = obj.has("label") && !obj.get("label").isJsonNull() ? obj.get("label").getAsString() : null;
            String terrainTypeName = obj.has("terrainType") ? obj.get("terrainType").getAsString() : null;
            boolean visible = obj.has("visible") ? obj.get("visible").getAsBoolean() : true;
            VoronoiControlPoint point = new VoronoiControlPoint(id, x, z, color);
            point.setSize(size);
            point.setWeight(weight);
            if (label != null && !label.isEmpty()) {
                point.setLabel(label);
            }
            if (terrainTypeName != null) {
                point.setTerrainType(terrainTypeName);
            }
            point.setVisible(visible);
            return point;
        }
        catch (Exception e) {
            WorldScape.LOGGER.warn("Failed to parse Voronoi control point JSON", (Throwable)e);
            return null;
        }
    }

    private static String readFileAsString(File file) throws IOException {
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);){
            String string;
            try (InputStreamReader reader = new InputStreamReader((InputStream)fis, StandardCharsets.UTF_8);){
                int n;
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[4096];
                while ((n = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, n);
                }
                string = sb.toString();
            }
            return string;
        }
    }

    public static boolean delete(File file) {
        File configFile = new File(file, FILE_NAME);
        File backupFile = new File(file, "voronoi_points.json.backup");
        boolean deleted = true;
        if (configFile.exists()) {
            deleted &= configFile.delete();
        }
        if (backupFile.exists()) {
            deleted &= backupFile.delete();
        }
        return deleted;
    }
}

