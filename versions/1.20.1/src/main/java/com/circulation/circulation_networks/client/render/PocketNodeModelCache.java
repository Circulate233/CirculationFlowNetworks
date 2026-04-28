package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.registry.CFNItems;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PocketNodeModelCache {

    private static final List<ResourceLocation> REGISTERED_MODELS = new ArrayList<>();
    public static final ResourceLocation PORT = register("pocket_node/pocket_port_node");
    public static final ResourceLocation CHARGING = register("pocket_node/pocket_charging_node");
    public static final ResourceLocation RELAY = register("pocket_node/pocket_relay_node");
    private static final Object2ObjectMap<ResourceLocation, BakedModel> BAKED_MODELS = new Object2ObjectOpenHashMap<>();

    private PocketNodeModelCache() {
    }

    private static ResourceLocation register(String path) {
        ResourceLocation location = new ResourceLocation(CirculationFlowNetworks.MOD_ID, "block/" + path);
        REGISTERED_MODELS.add(location);
        return location;
    }

    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation location : REGISTERED_MODELS) {
            event.register(location);
        }
    }

    public static BakedModel get(ItemStack stack) {
        ResourceLocation location = resolveLocation(stack);
        if (location == null) {
            return Minecraft.getInstance().getModelManager().getMissingModel();
        }
        return BAKED_MODELS.computeIfAbsent(location, PocketNodeModelCache::loadModel);
    }

    public static boolean isGui3d(ItemStack stack) {
        return get(stack).isGui3d();
    }

    public static void clear() {
        BAKED_MODELS.clear();
    }

    private static BakedModel loadModel(ResourceLocation location) {
        return Minecraft.getInstance().getModelManager().getModel(location);
    }

    private static @Nullable ResourceLocation resolveLocation(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() == CFNItems.pocketPortNode) {
            return PORT;
        }
        if (stack.getItem() == CFNItems.pocketChargingNode) {
            return CHARGING;
        }
        if (stack.getItem() == CFNItems.pocketRelayNode) {
            return RELAY;
        }
        return null;
    }
}
