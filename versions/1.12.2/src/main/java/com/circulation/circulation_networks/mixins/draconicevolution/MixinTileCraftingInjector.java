package com.circulation.circulation_networks.mixins.draconicevolution;

import com.brandon3055.draconicevolution.api.fusioncrafting.IFusionCraftingInventory;
import com.brandon3055.draconicevolution.blocks.tileentity.TileCraftingInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = TileCraftingInjector.class, remap = false)
public abstract class MixinTileCraftingInjector {

    @Shadow
    public IFusionCraftingInventory currentCraftingInventory;

    @Shadow
    protected abstract boolean validateCraftingInventory();

    /**
     * @author circulation
     * @reason 我完全不能知道聚合注入器为什么不返回正确的存储上限
     */
    @Overwrite
    public long getExtendedCapacity() {
        this.validateCraftingInventory();
        if (this.currentCraftingInventory != null) {
            return this.currentCraftingInventory.getIngredientEnergyCost();
        }

        return 0L;
    }

}
