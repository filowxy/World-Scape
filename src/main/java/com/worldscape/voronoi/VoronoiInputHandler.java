/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.KeyMapping
 *  net.neoforged.api.distmarker.Dist
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.fml.common.EventBusSubscriber
 *  net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
 */
package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiInputEvents;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(value={Dist.CLIENT}, modid="worldscape")
public class VoronoiInputHandler {
    public static final KeyMapping KEY_TOGGLE_OVERLAY = new KeyMapping("key.worldscape.toggle_overlay", 86, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_CREATE_POINT = new KeyMapping("key.worldscape.create_point", 67, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_DELETE_SELECTED = new KeyMapping("key.worldscape.delete_selected", 261, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_SWITCH_VIEW_MODE = new KeyMapping("key.worldscape.switch_view_mode", 258, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_TOGGLE_INFO_PANEL = new KeyMapping("key.worldscape.toggle_info_panel", 72, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_SELECT_ALL = new KeyMapping("key.worldscape.select_all", 65, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_DESELECT_ALL = new KeyMapping("key.worldscape.deselect_all", 256, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_MOVE_UP = new KeyMapping("key.worldscape.move_up", 87, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_MOVE_DOWN = new KeyMapping("key.worldscape.move_down", 83, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_MOVE_LEFT = new KeyMapping("key.worldscape.move_left", 65, "key.category.worldscape.voronoi");
    public static final KeyMapping KEY_MOVE_RIGHT = new KeyMapping("key.worldscape.move_right", 68, "key.category.worldscape.voronoi");
    static final SelectionBox selectionBox = new SelectionBox();

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_TOGGLE_OVERLAY);
        event.register(KEY_CREATE_POINT);
        event.register(KEY_DELETE_SELECTED);
        event.register(KEY_SWITCH_VIEW_MODE);
        event.register(KEY_TOGGLE_INFO_PANEL);
        event.register(KEY_SELECT_ALL);
        event.register(KEY_DESELECT_ALL);
        event.register(KEY_MOVE_UP);
        event.register(KEY_MOVE_DOWN);
        event.register(KEY_MOVE_LEFT);
        event.register(KEY_MOVE_RIGHT);
    }

    public static SelectionBox getCurrentSelectionBox() {
        return selectionBox.isActive() ? selectionBox : null;
    }

    public static void resetState() {
        VoronoiInputEvents.resetState();
    }

    public static class SelectionBox {
        public int startX;
        public int startY;
        public int endX;
        public int endY;
        private boolean active = false;

        public void begin(int x, int y) {
            this.startX = x;
            this.startY = y;
            this.endX = x;
            this.endY = y;
            this.active = true;
        }

        public void update(int x, int y) {
            if (this.active) {
                this.endX = x;
                this.endY = y;
            }
        }

        public void end() {
            this.active = false;
        }

        public boolean isActive() {
            return this.active;
        }

        public int getMinX() {
            return Math.min(this.startX, this.endX);
        }

        public int getMinY() {
            return Math.min(this.startY, this.endY);
        }

        public int getMaxX() {
            return Math.max(this.startX, this.endX);
        }

        public int getMaxY() {
            return Math.max(this.startY, this.endY);
        }
    }
}

