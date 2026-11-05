package com.circulation.circulation_networks.mixins.mekanism;

import com.circulation.circulation_networks.api.MekanismEnergyPermissions;
import mekanism.api.AutomationType;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.function.Predicate;

@Mixin(value = BasicEnergyContainer.class, remap = false)
public abstract class MixinBasicEnergyContainer implements MekanismEnergyPermissions {
    @Shadow @Final protected Predicate<AutomationType> canExtract;
    @Shadow @Final protected Predicate<AutomationType> canInsert;

    @Override
    public boolean cfn_canExtractEnergy() { return canExtract.test(AutomationType.EXTERNAL); }

    @Override
    public boolean cfn_canInsertEnergy() { return canInsert.test(AutomationType.EXTERNAL); }
}
