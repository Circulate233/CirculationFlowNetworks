package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.packets.UpdateItemModeMessage;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.utils.Functions;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

@SideOnly(Side.CLIENT)
public class InspectionToolHandler {
    public static final InspectionToolHandler INSTANCE = new InspectionToolHandler();

    private final Minecraft mc = FMLClientHandler.instance().getClient();

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (mc.player != null && mc.player.isSneaking()) {
            ItemStack stack = mc.player.getHeldItemMainhand();
            int delta = -Mouse.getEventDWheel();
            if (delta % 120 == 0) {
                delta = delta / 120;
            }
            if (delta % 80 == 0) {
                delta = delta / 80;
            }
            if (delta != 0 && stack.getItem() == RegistryItems.inspectionTool) {
                final var tag = Functions.getOrCreateTagCompound(stack);
                int mode = tag.getInteger("mode") + delta;
                tag.setInteger("mode", mode);

                CirculationFlowNetworks.NET_CHANNEL.sendToServer(new UpdateItemModeMessage(mode));
                event.setCanceled(true);
            }
        }
    }
}