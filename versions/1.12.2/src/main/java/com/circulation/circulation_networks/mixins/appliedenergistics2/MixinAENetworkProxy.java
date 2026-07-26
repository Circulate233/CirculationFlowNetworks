package com.circulation.circulation_networks.mixins.appliedenergistics2;

import appeng.api.networking.IGridHost;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileEnergyAcceptor;
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AENetworkProxy.class, remap = false)
public abstract class MixinAENetworkProxy {

    @Inject(method = "gridChanged()V", at = @At("RETURN"), remap = false)
    private void gridChanged(CallbackInfo ci) {
        IGridHost machine = this.getMachine();
        if (machine instanceof TileController || machine instanceof TileEnergyAcceptor) {
            EnergyMachineManager.INSTANCE.onBlockEntityReady(CFNBlockEntityEx.cfn_cast(machine));
        }
    }

    @Shadow
    public abstract IGridHost getMachine();
}
