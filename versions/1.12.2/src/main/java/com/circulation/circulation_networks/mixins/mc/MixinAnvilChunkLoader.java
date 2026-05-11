package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.manager.EnergyMachineManager;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnvilChunkLoader.class, remap = false)
public class MixinAnvilChunkLoader {

    @Inject(method = "loadEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;addTileEntity(Lnet/minecraft/tileentity/TileEntity;)V", shift = At.Shift.BEFORE))
    public void loadEntitiesStart(CallbackInfo ci) {
        EnergyMachineManager.INSTANCE.setCanAddManchine(false);
    }

}