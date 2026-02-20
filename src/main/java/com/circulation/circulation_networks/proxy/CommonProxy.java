package com.circulation.circulation_networks.proxy;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.energy.handler.CEHandler;
import com.circulation.circulation_networks.energy.manager.CEHandlerManager;
import com.circulation.circulation_networks.energy.manager.EUHandlerManager;
import com.circulation.circulation_networks.energy.manager.FEHandlerManager;
import com.circulation.circulation_networks.energy.manager.MEKHandlerManager;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.jetbrains.annotations.Nullable;

import static com.circulation.circulation_networks.CirculationFlowNetworks.NET_CHANNEL;

public class CommonProxy {

    @CapabilityInject(CEHandler.class)
    public static Capability<CEHandler> ceHandlerCapability;
    public static Capability<INode> nodeCapability;
    private int id = 0;

    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
        CapabilityManager.INSTANCE.register(CEHandler.class, new CEHandlerIStorage(), () -> null);
        CapabilityManager.INSTANCE.register(INode.class, new INodeIStorage(), () -> null);
    }

    public void init() {
        RegistryEnergyHandler.registryEnergyHandler(new CEHandlerManager());
        RegistryEnergyHandler.registryEnergyHandler(new FEHandlerManager());
        if (Loader.isModLoaded("mekanism"))
            RegistryEnergyHandler.registryEnergyHandler(new MEKHandlerManager());
        if (Loader.isModLoaded("ic2")) {
            RegistryEnergyHandler.registryEnergyHandler(EUHandlerManager.INSTANCE);
            MinecraftForge.EVENT_BUS.register(EUHandlerManager.INSTANCE);
        }
        try {
            RegistryEnergyHandler.blackListClass.add(Class.forName("sonar.fluxnetworks.common.tileentity.TileFluxCore"));
        } catch (ClassNotFoundException ignored) {

        }
    }

    public void postInit() {
        RegistryEnergyHandler.lock();
    }

    public <T extends Packet<T>> void registerMessage(Class<T> aClass, Side side) {
        NET_CHANNEL.registerMessage(aClass, aClass, id++, side);
    }

    @SubscribeEvent
    public void registryItem(RegistryEvent.Register<Item> event) {
        RegistryItems.registerItems(event);
    }

    @SubscribeEvent
    public void registryBlock(RegistryEvent.Register<Block> event) {
        RegistryBlocks.registerBlocks(event);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EnergyMachineManager.INSTANCE.onServerTick();
    }

    private static class CEHandlerIStorage implements Capability.IStorage<CEHandler> {
        @Override
        public @Nullable NBTBase writeNBT(Capability<CEHandler> capability, CEHandler instance, EnumFacing side) {
            return new NBTTagLong(instance.getEnergy().getEnergy());
        }

        @Override
        public void readNBT(Capability<CEHandler> capability, CEHandler instance, EnumFacing side, NBTBase nbt) {
            instance.getEnergy().setEnergy(((NBTTagLong) nbt).getLong());
        }
    }

    private static class INodeIStorage implements Capability.IStorage<INode> {
        @Override
        public @Nullable NBTBase writeNBT(Capability<INode> capability, INode instance, EnumFacing side) {
            return null;
        }

        @Override
        public void readNBT(Capability<INode> capability, INode instance, EnumFacing side, NBTBase nbt) {

        }
    }
}