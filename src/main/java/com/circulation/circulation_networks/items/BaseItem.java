package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import static com.circulation.circulation_networks.CirculationFlowNetworks.CREATIVE_TAB;

public abstract class BaseItem extends Item {

    protected BaseItem(String name) {
        this.setRegistryName(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name));
        this.setTranslationKey(CirculationFlowNetworks.MOD_ID + "." + name);
        this.setCreativeTab(CREATIVE_TAB);
    }

}
