package com.circulation.circulation_networks.mixins.mc;

import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.circulation.circulation_networks.CirculationFlowNetworks.loaderInit;

@Mixin(value = AnvilChunkLoader.class, remap = false)
public class MixinAnvilChunkLoader {

    @Inject(method = "loadEntities", at = @At("HEAD"))
    public void loadEntitiesStart(CallbackInfo ci) {
        loaderInit = false;
    }

    @Inject(method = "loadEntities", at = @At("TAIL"))
    public void loadEntitiesStop(CallbackInfo ci) {
        loaderInit = true;
    }
}
