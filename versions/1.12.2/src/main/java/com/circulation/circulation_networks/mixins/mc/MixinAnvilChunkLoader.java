package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.manager.EnergyMachineManager;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnvilChunkLoader.class, remap = false)
public class MixinAnvilChunkLoader {

    @Inject(method = "loadEntities", at = @At("HEAD"))
    public void loadEntitiesStart(CallbackInfo ci) {
        EnergyMachineManager.INSTANCE.setCanAddManchine(false);
    }

}
