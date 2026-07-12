package com.circulation.circulation_networks.mixins.appliedenergistics2;

import appeng.api.networking.IGridHost;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileEnergyAcceptor;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "appeng.me.helpers.AENetworkProxy", remap = false)
public abstract class MixinAENetworkProxy {

    @Inject(method = "gridChanged()V", at = @At("RETURN"), remap = false)
    private void gridChanged(CallbackInfo ci) {
        IGridHost machine = this.getMachine();
        if (machine instanceof TileController || machine instanceof TileEnergyAcceptor) {
            EnergyMachineManager.INSTANCE.onBlockEntityReady((TileEntity) machine);
        }
    }

    @Shadow
    public abstract IGridHost getMachine();
}
