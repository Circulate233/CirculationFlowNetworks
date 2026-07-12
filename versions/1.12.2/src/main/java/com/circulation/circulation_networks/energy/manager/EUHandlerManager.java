package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.energy.handler.EUHandler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.item.ElectricItem;
import ic2.core.block.wiring.TileEntityCable;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class EUHandlerManager implements IEnergyHandlerManager {

    public static final EUHandlerManager INSTANCE = new EUHandlerManager();

    private final Reference2LongOpenHashMap<IEnergyTile> lifecycleGenerations = new Reference2LongOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IEnergyTile, EnergyTilePosition> boundPositions =
        new Reference2ObjectOpenHashMap<>();
    private long lifecycleGeneration;

    private EUHandlerManager() {
        lifecycleGenerations.defaultReturnValue(Long.MIN_VALUE);
    }

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return EUHandler.supportsEnergyTile(EUHandler.resolveEnergyTile(tileEntity));
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return ElectricItem.manager.getMaxCharge(itemStack) > 0;
    }

    @Override
    public Class<EUHandler> getEnergyHandlerClass() {
        return EUHandler.class;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new EUHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new EUHandler();
    }

    @Override
    public String getUnit() {
        return "EU";
    }

    @Override
    public double getMultiplying() {
        return 4;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEnergyTileLoad(EnergyTileLoadEvent event) {
        if (!(event.getWorld() instanceof WorldServer server)) return;
        IEnergyTile energyTile = event.tile;
        long generation = nextLifecycleGeneration(energyTile);
        queueLoad(server, energyTile, generation);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEnergyTileUnload(EnergyTileUnloadEvent event) {
        if (!(event.getWorld() instanceof WorldServer server)) return;
        IEnergyTile energyTile = event.tile;
        long generation = nextLifecycleGeneration(energyTile);
        EnergyTilePosition invalidatedPosition = removeBoundPosition(energyTile);
        if (invalidatedPosition == null) {
            invalidatedPosition = resolvePosition(server, energyTile);
        }
        queueUnload(server, energyTile, invalidatedPosition, generation);
    }

    synchronized long nextLifecycleGeneration(IEnergyTile energyTile) {
        if (lifecycleGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("IC2 lifecycle generation exhausted");
        }
        long generation = ++lifecycleGeneration;
        lifecycleGenerations.put(energyTile, generation);
        return generation;
    }

    synchronized EnergyTilePosition removeBoundPosition(IEnergyTile energyTile) {
        return boundPositions.remove(energyTile);
    }

    synchronized boolean trackBoundPosition(IEnergyTile energyTile,
                                            EnergyTilePosition position,
                                            long generation) {
        if (lifecycleGenerations.getLong(energyTile) != generation) {
            return false;
        }
        boundPositions.put(energyTile, position);
        return true;
    }

    private void queueLoad(WorldServer eventWorld, IEnergyTile energyTile, long generation) {
        try {
            if (energyTile instanceof TileEntityCable) {
                return;
            }
            EnergyTilePosition position = resolvePosition(eventWorld, energyTile);
            if (position == null || !trackBoundPosition(energyTile, position, generation)) {
                return;
            }
            EnergyMachineManager.INSTANCE.onBlockPositionReady(eventWorld, position.blockPosition());
        } catch (RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.error("Failed to queue IC2 energy tile load generation {}", generation,
                exception);
        }
    }

    private void queueUnload(WorldServer eventWorld,
                             IEnergyTile energyTile,
                             EnergyTilePosition invalidatedPosition,
                             long generation) {
        try {
            if (claimManagerRemoval(energyTile, invalidatedPosition, generation)) {
                EnergyMachineManager.INSTANCE.onManagerUnavailableAtPosition(
                    eventWorld, invalidatedPosition.blockPosition(), this
                );
            }
        } catch (RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.error("Failed to queue IC2 energy tile unload generation {}", generation,
                exception);
        }
    }

    synchronized boolean claimManagerRemoval(IEnergyTile energyTile,
                                             EnergyTilePosition invalidatedPosition,
                                             long generation) {
        if (lifecycleGenerations.getLong(energyTile) != generation) {
            return false;
        }
        lifecycleGenerations.removeLong(energyTile);
        return invalidatedPosition != null && !hasBoundPosition(invalidatedPosition);
    }

    public synchronized void onServerStop() {
        lifecycleGenerations.clear();
        boundPositions.clear();
        lifecycleGeneration = 0L;
    }

    private boolean hasBoundPosition(EnergyTilePosition position) {
        if (position == null) {
            return false;
        }
        for (EnergyTilePosition boundPosition : boundPositions.values()) {
            if (boundPosition.equals(position)) {
                return true;
            }
        }
        return false;
    }

    private static EnergyTilePosition resolvePosition(WorldServer eventWorld, IEnergyTile energyTile) {
        if (EnergyNet.instance.getWorld(energyTile) != eventWorld) {
            return null;
        }
        BlockPos position = EnergyNet.instance.getPos(energyTile);
        return position == null
            ? null
            : new EnergyTilePosition(eventWorld.provider.getDimension(), position.toLong());
    }

    static final class EnergyTilePosition {
        private final int dimensionId;
        private final long packedPosition;

        EnergyTilePosition(int dimensionId, long packedPosition) {
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
        }

        BlockPos blockPosition() {
            return BlockPos.fromLong(packedPosition);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EnergyTilePosition position)) {
                return false;
            }
            return dimensionId == position.dimensionId && packedPosition == position.packedPosition;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(dimensionId);
            return 31 * result + Long.hashCode(packedPosition);
        }
    }
}
