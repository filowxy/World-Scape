package com.worldscape.mixin;

import com.worldscape.WorldScape;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerChunkCache.class})
public abstract class ServerChunkCacheMixin {
    @Inject(method={"<init>"}, at={@At(value="RETURN")}, remap=false)
    private void onConstructed(CallbackInfo ci) {
        ServerChunkCache cache = (ServerChunkCache)(Object)this;
        Level level = cache.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        if (serverLevel.dimension() == Level.OVERWORLD) {
            try {
                ChunkGenerator generator = cache.getGenerator();
                WorldScape.LOGGER.info("[World Scape] ServerChunkCache constructed for overworld, using generator: {}", (Object)generator.getClass().getSimpleName());
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }
}

