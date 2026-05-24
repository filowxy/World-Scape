package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiCamera;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiControlPointManager;
import com.worldscape.voronoi.VoronoiInputHandler;
import com.worldscape.voronoi.VoronoiOverlayRenderer;
import com.worldscape.voronoi.WorldScapeVoronoiSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(value={Dist.CLIENT}, modid="worldscape")
public class VoronoiInputEvents {
    private static boolean shiftHeld = false;
    private static boolean mouseDown = false;
    private static long lastClickTime = 0L;
    private static final long DOUBLE_CLICK_THRESHOLD = 300L;
    private static final float MOVE_SPEED_PER_TICK = 20.0f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        VoronoiInputEvents.handleMovement(mc);
        VoronoiInputEvents.checkKeyMappings(mc);
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!WorldScapeVoronoiSystem.isEnabled()) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        if (!WorldScapeVoronoiSystem.isEnabled()) {
            return;
        }
        event.setMouseSensitivity(0.0);
    }

    @SubscribeEvent
    public static void onMouseButtonPost(InputEvent.MouseButton.Post event) {
        boolean isPressed;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        int button = event.getButton();
        boolean bl = isPressed = event.getAction() == 1;
        if (button == 0) {
            if (isPressed) {
                int mouseX = (int)mc.mouseHandler.xpos();
                int mouseY = (int)mc.mouseHandler.ypos();
                if (shiftHeld) {
                    VoronoiInputHandler.selectionBox.begin(mouseX, mouseY);
                    mouseDown = true;
                } else {
                    VoronoiInputEvents.handleLeftClick(mc, mouseX, mouseY);
                }
            } else {
                if (mouseDown && VoronoiInputHandler.selectionBox.isActive()) {
                    VoronoiInputEvents.completeBoxSelection(mc);
                    VoronoiInputHandler.selectionBox.end();
                }
                mouseDown = false;
            }
        } else if (button == 1 && isPressed) {
            VoronoiInputEvents.handleRightClick(mc);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        VoronoiCamera camera;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        if (!WorldScapeVoronoiSystem.isEnabled()) {
            return;
        }
        double scrollDelta = event.getScrollDeltaY();
        if (scrollDelta != 0.0 && (camera = WorldScapeVoronoiSystem.getCamera()) != null) {
            camera.scrollZoom(scrollDelta);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getKey() == 340 || event.getKey() == 344) {
            shiftHeld = event.getAction() == 1 || event.getAction() == 2;
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (!WorldScapeVoronoiSystem.isInitialized() || !WorldScapeVoronoiSystem.isEnabled()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            WorldScapeVoronoiSystem.save();
        }
    }

    private static void handleMovement(Minecraft mc) {
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        if (camera == null || !WorldScapeVoronoiSystem.isEnabled()) {
            return;
        }
        float dx = 0.0f;
        float dz = 0.0f;
        if (VoronoiInputHandler.KEY_MOVE_UP.isDown()) {
            dz -= 20.0f;
        }
        if (VoronoiInputHandler.KEY_MOVE_DOWN.isDown()) {
            dz += 20.0f;
        }
        if (VoronoiInputHandler.KEY_MOVE_LEFT.isDown()) {
            dx -= 20.0f;
        }
        if (VoronoiInputHandler.KEY_MOVE_RIGHT.isDown()) {
            dx += 20.0f;
        }
        if (dx != 0.0f || dz != 0.0f) {
            camera.move(dx, dz);
        }
    }

    private static void checkKeyMappings(Minecraft mc) {
        if (!WorldScapeVoronoiSystem.isEnabled()) {
            return;
        }
        if (VoronoiInputHandler.KEY_TOGGLE_OVERLAY.consumeClick()) {
            WorldScapeVoronoiSystem.toggle();
            return;
        }
        if (VoronoiInputHandler.KEY_SWITCH_VIEW_MODE.consumeClick()) {
            VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
            if (camera != null) {
                camera.toggleViewMode();
            }
            return;
        }
        if (VoronoiInputHandler.KEY_TOGGLE_INFO_PANEL.consumeClick()) {
            VoronoiOverlayRenderer.toggleInfoPanel();
            return;
        }
        if (VoronoiInputHandler.KEY_DELETE_SELECTED.consumeClick()) {
            VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
            if (manager != null) {
                manager.deleteSelected();
            }
            return;
        }
        if (VoronoiInputHandler.KEY_SELECT_ALL.consumeClick()) {
            VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
            if (manager != null) {
                manager.selectAll();
            }
            return;
        }
        if (VoronoiInputHandler.KEY_DESELECT_ALL.consumeClick()) {
            VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
            if (manager != null) {
                manager.deselectAll();
            }
            return;
        }
        if (VoronoiInputHandler.KEY_CREATE_POINT.consumeClick()) {
            VoronoiInputEvents.createPointAtCursor(mc);
        }
    }

    private static void handleLeftClick(Minecraft mc, int mouseX, int mouseY) {
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        if (manager == null || camera == null) {
            return;
        }
        double[] worldPos = camera.screenToWorld(mouseX, mouseY, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        int worldX = (int)worldPos[0];
        int worldZ = (int)worldPos[1];
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 300L) {
            manager.createPoint(worldX, worldZ, -11886849);
            lastClickTime = 0L;
            return;
        }
        lastClickTime = currentTime;
        VoronoiControlPoint nearest = manager.findNearest(worldX, worldZ, 50);
        if (nearest != null) {
            manager.selectSingle(nearest.getId());
        }
        VoronoiInputEvents.updateHoverState(mc, mouseX, mouseY);
    }

    private static void handleRightClick(Minecraft mc) {
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        if (manager != null) {
            manager.deselectAll();
        }
    }

    private static void completeBoxSelection(Minecraft mc) {
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        if (manager == null || camera == null) {
            return;
        }
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        double[] minWorld = camera.screenToWorld(VoronoiInputHandler.selectionBox.getMinX(), VoronoiInputHandler.selectionBox.getMinY(), screenWidth, screenHeight);
        double[] maxWorld = camera.screenToWorld(VoronoiInputHandler.selectionBox.getMaxX(), VoronoiInputHandler.selectionBox.getMaxY(), screenWidth, screenHeight);
        int minWorldX = Math.min((int)minWorld[0], (int)maxWorld[0]);
        int minWorldZ = Math.min((int)minWorld[1], (int)maxWorld[1]);
        int maxWorldX = Math.max((int)minWorld[0], (int)maxWorld[0]);
        int maxWorldZ = Math.max((int)minWorld[1], (int)maxWorld[1]);
        manager.selectBox(minWorldX, minWorldZ, maxWorldX, maxWorldZ);
    }

    private static void createPointAtCursor(Minecraft mc) {
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        if (manager == null || camera == null) {
            return;
        }
        int mouseX = (int)mc.mouseHandler.xpos();
        int mouseY = (int)mc.mouseHandler.ypos();
        double[] worldPos = camera.screenToWorld(mouseX, mouseY, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        int worldX = (int)worldPos[0];
        int worldZ = (int)worldPos[1];
        manager.createPoint(worldX, worldZ, -11886849);
    }

    private static void updateHoverState(Minecraft mc, int mouseX, int mouseY) {
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        if (manager == null || camera == null) {
            return;
        }
        double[] worldPos = camera.screenToWorld(mouseX, mouseY, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        int worldX = (int)worldPos[0];
        int worldZ = (int)worldPos[1];
        VoronoiControlPoint nearest = manager.findNearest(worldX, worldZ, 50);
        VoronoiOverlayRenderer.setHoveredPoint(nearest);
    }

    public static void resetState() {
        VoronoiInputHandler.selectionBox.end();
        mouseDown = false;
        shiftHeld = false;
        lastClickTime = 0L;
    }

    public static boolean isShiftHeld() {
        return shiftHeld;
    }
}

