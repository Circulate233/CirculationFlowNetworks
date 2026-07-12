package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
//? if <1.20
import com.github.bsideup.jabel.Desugar;
//~ mc_imports
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
//~ if >=1.20 'net.minecraft.util.math.BlockPos' -> 'net.minecraft.core.BlockPos' {
import net.minecraft.util.math.BlockPos;
//~}
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Isolates third-party energy capability failures at every manager-owned call site.
 */
final class EnergyHandlerRuntime {

    private static final long FAILURE_LOG_INTERVAL_EPOCHS = 100L;
    private static final int UNKNOWN_DIMENSION = Integer.MIN_VALUE;
    private static final long UNKNOWN_POSITION = Long.MIN_VALUE;
    private static final Object2LongMap<FailureKey> lastFailureLogEpochs = new Object2LongOpenHashMap<>();
    private static long currentEpoch = Long.MIN_VALUE;
    private static long nextBindingGeneration = 1L;

    private EnergyHandlerRuntime() {
    }

    static long nextBindingGeneration() {
        if (nextBindingGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("Energy handler binding generation exhausted");
        }
        return nextBindingGeneration++;
    }

    static FailureContext machineContext(int dimensionId, long packedPosition, long bindingGeneration) {
        return new FailureContext(dimensionId, packedPosition, bindingGeneration);
    }

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    @Nullable
    static MachineBindingIndex.Binding bindBlockEntity(TileEntity blockEntity,
                                                         IEnergyHandler handler,
                                                         @Nullable MappedEnergyHandlerProvider mappedProvider,
                                                         FailureContext failureContext) {
        //~}
        Objects.requireNonNull(failureContext, "failureContext");
        try {
            return MachineBindingIndex.INSTANCE.bindBlockEntity(blockEntity, handler, mappedProvider);
        } catch (EnergyHandlerNotReadyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log(failureContext, handler, "bindBlockEntity", exception);
            return null;
        }
    }

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    static void unbindBlockEntity(TileEntity blockEntity) {
        //~}
        try {
            MachineBindingIndex.INSTANCE.unbindBlockEntity(blockEntity);
        } catch (RuntimeException exception) {
            logIndex("unbindBlockEntity", exception);
        }
    }

    static void beginBindings(long epoch) {
        currentEpoch = epoch;
        pruneFailureLogEpochs(epoch);
        try {
            MachineBindingIndex.INSTANCE.beginServerTick(epoch);
        } catch (RuntimeException exception) {
            logIndex("beginServerTick", exception);
        }
    }

    static void endBindings(long epoch) {
        try {
            MachineBindingIndex.INSTANCE.endServerTick(epoch);
        } catch (RuntimeException exception) {
            logIndex("endServerTick", exception);
        }
    }

    static void stopBindings() {
        try {
            MachineBindingIndex.INSTANCE.onServerStop();
        } catch (RuntimeException exception) {
            logIndex("onServerStop", exception);
        } finally {
            lastFailureLogEpochs.clear();
            currentEpoch = Long.MIN_VALUE;
        }
    }

    @Nullable
    static IEnergyHandler bindItem(ItemStack stack, @Nullable HubNode.HubMetadata hubMetadata) {
        try {
            return MachineBindingIndex.INSTANCE.bindItem(stack, hubMetadata);
        } catch (RuntimeException exception) {
            logIndex("bindItem", exception);
            return null;
        }
    }

    static void unbindItem(IEnergyHandler handler) {
        try {
            handler.unbindItem();
        } catch (RuntimeException exception) {
            log(FailureContext.UNKNOWN, handler, "unbindItem", exception);
        }
    }

    static HandlerBindingPolicy policy(IEnergyHandler handler) {
        HandlerBindingPolicy policy = handler.bindingPolicy();
        if (policy == null) {
            throw new IllegalStateException("Handler returned a null binding policy");
        }
        return policy;
    }

    static IEnergyHandler.EnergyType type(IEnergyHandler handler, @Nullable HubNode.HubMetadata hubMetadata) {
        return type(handler, hubMetadata, FailureContext.UNKNOWN);
    }

    static IEnergyHandler.EnergyType type(IEnergyHandler handler,
                                          @Nullable HubNode.HubMetadata hubMetadata,
                                          FailureContext context) {
        try {
            IEnergyHandler.EnergyType type = handler.getType(hubMetadata);
            return type != null ? type : IEnergyHandler.EnergyType.INVALID;
        } catch (RuntimeException exception) {
            log(context, handler, "getType", exception);
            return IEnergyHandler.EnergyType.INVALID;
        }
    }

    static EnergyAmount canExtract(IEnergyHandler handler, @Nullable HubNode.HubMetadata hubMetadata) {
        return canExtract(handler, hubMetadata, FailureContext.UNKNOWN);
    }

    static EnergyAmount canExtract(IEnergyHandler handler,
                                   @Nullable HubNode.HubMetadata hubMetadata,
                                   FailureContext context) {
        try {
            return requireAmount(handler.canExtractValue(hubMetadata), context, handler, "canExtractValue");
        } catch (RuntimeException exception) {
            log(context, handler, "canExtractValue", exception);
            return EnergyAmount.obtain(0L);
        }
    }

    static EnergyAmount canReceive(IEnergyHandler handler, @Nullable HubNode.HubMetadata hubMetadata) {
        return canReceive(handler, hubMetadata, FailureContext.UNKNOWN);
    }

    static EnergyAmount canReceive(IEnergyHandler handler,
                                   @Nullable HubNode.HubMetadata hubMetadata,
                                   FailureContext context) {
        try {
            return requireAmount(handler.canReceiveValue(hubMetadata), context, handler, "canReceiveValue");
        } catch (RuntimeException exception) {
            log(context, handler, "canReceiveValue", exception);
            return EnergyAmount.obtain(0L);
        }
    }

    static boolean canExtract(IEnergyHandler handler,
                              IEnergyHandler receiver,
                              @Nullable HubNode.HubMetadata hubMetadata) {
        return canExtract(handler, receiver, hubMetadata, FailureContext.UNKNOWN);
    }

    static boolean canExtract(IEnergyHandler handler,
                              IEnergyHandler receiver,
                              @Nullable HubNode.HubMetadata hubMetadata,
                              FailureContext context) {
        try {
            return handler.canExtract(receiver, hubMetadata);
        } catch (RuntimeException exception) {
            log(context, handler, "canExtract", exception);
            return false;
        }
    }

    static boolean canReceive(IEnergyHandler handler,
                              IEnergyHandler sender,
                              @Nullable HubNode.HubMetadata hubMetadata) {
        return canReceive(handler, sender, hubMetadata, FailureContext.UNKNOWN);
    }

    static boolean canReceive(IEnergyHandler handler,
                              IEnergyHandler sender,
                              @Nullable HubNode.HubMetadata hubMetadata,
                              FailureContext context) {
        try {
            return handler.canReceive(sender, hubMetadata);
        } catch (RuntimeException exception) {
            log(context, handler, "canReceive", exception);
            return false;
        }
    }

    static EnergyAmount extract(IEnergyHandler handler,
                                EnergyAmount maximum,
                                @Nullable HubNode.HubMetadata hubMetadata) {
        return extract(handler, maximum, hubMetadata, FailureContext.UNKNOWN);
    }

    static EnergyAmount extract(IEnergyHandler handler,
                                EnergyAmount maximum,
                                @Nullable HubNode.HubMetadata hubMetadata,
                                FailureContext context) {
        try {
            return requireAmount(handler.extractEnergy(maximum, hubMetadata), context, handler, "extractEnergy");
        } catch (RuntimeException exception) {
            log(context, handler, "extractEnergy", exception);
            return EnergyAmount.obtain(0L);
        }
    }

    static EnergyAmount receive(IEnergyHandler handler,
                                EnergyAmount maximum,
                                @Nullable HubNode.HubMetadata hubMetadata) {
        return receive(handler, maximum, hubMetadata, FailureContext.UNKNOWN);
    }

    static EnergyAmount receive(IEnergyHandler handler,
                                EnergyAmount maximum,
                                @Nullable HubNode.HubMetadata hubMetadata,
                                FailureContext context) {
        try {
            return requireAmount(handler.receiveEnergy(maximum, hubMetadata), context, handler, "receiveEnergy");
        } catch (RuntimeException exception) {
            log(context, handler, "receiveEnergy", exception);
            return EnergyAmount.obtain(0L);
        }
    }

    static HandlerBindingPolicy bindingPolicy(MachineBindingIndex.Binding binding) {
        return binding.policy();
    }

    @Nullable
    static MachineTransferAccount account(MachineBindingIndex.Binding binding,
                                          IEnergyHandler directHandler,
                                          FailureContext context) {
        if (!binding.isActive()) {
            return null;
        }
        try {
            return binding.account();
        } catch (RuntimeException exception) {
            log(context, directHandler, "transferAccount", exception);
            return null;
        }
    }

    private static EnergyAmount requireAmount(@Nullable EnergyAmount amount,
                                              FailureContext context,
                                              IEnergyHandler handler,
                                              String operation) {
        if (amount != null) {
            return amount;
        }
        log(context, handler, operation, new IllegalStateException("Energy handler returned null"));
        return EnergyAmount.obtain(0L);
    }

    private static void log(FailureContext context,
                            IEnergyHandler handler,
                            String operation,
                            RuntimeException exception) {
        if (!shouldLog(context, handler, operation)) {
            return;
        }
        CirculationFlowNetworks.LOGGER.error(
            "Energy handler {} failed during {} at dimension {} position {} binding generation {}",
            handler.getClass().getName(), operation, context.dimensionId, formatPosition(context.packedPosition),
            context.bindingGeneration, exception
        );
    }

    static void logContractViolation(IEnergyHandler handler,
                                     String operation,
                                     FailureContext context,
                                     RuntimeException exception) {
        log(context, handler, operation + ":contract", exception);
    }

    /** Formats packed machine coordinates only on an error path. */
    static String formatPosition(long packedPosition) {
        if (packedPosition == UNKNOWN_POSITION) {
            return "unknown";
        }
        //~ if >=1.20 '.fromLong(' -> '.of(' {
        BlockPos position = BlockPos.fromLong(packedPosition);
        //~}
        return "(" + position.getX() + ", " + position.getY() + ", " + position.getZ() + ")";
    }

    private static void logIndex(String operation, RuntimeException exception) {
        if (!shouldLog(FailureContext.UNKNOWN, null, "bindingIndex:" + operation)) {
            return;
        }
        CirculationFlowNetworks.LOGGER.error("Energy handler binding index failed during {}", operation, exception);
    }

    private static boolean shouldLog(FailureContext context, @Nullable IEnergyHandler handler, String operation) {
        long epoch = currentEpoch;
        FailureKey key = new FailureKey(context.dimensionId, context.packedPosition,
            handler == null ? 0 : System.identityHashCode(handler), operation, context.bindingGeneration);
        long lastEpoch = lastFailureLogEpochs.getLong(key);
        if (lastEpoch != Long.MIN_VALUE && epoch != Long.MIN_VALUE
            && epoch >= lastEpoch && epoch - lastEpoch < FAILURE_LOG_INTERVAL_EPOCHS) {
            return false;
        }
        lastFailureLogEpochs.put(key, epoch);
        return true;
    }

    private static void pruneFailureLogEpochs(long epoch) {
        if (lastFailureLogEpochs.size() < 2048) {
            return;
        }
        lastFailureLogEpochs.entrySet().removeIf(entry -> epoch - entry.getValue() > FAILURE_LOG_INTERVAL_EPOCHS);
    }

    static final class FailureContext {
        static final FailureContext UNKNOWN = new FailureContext(UNKNOWN_DIMENSION, UNKNOWN_POSITION, 0L);

        private final int dimensionId;
        private final long packedPosition;
        private final long bindingGeneration;

        private FailureContext(int dimensionId, long packedPosition, long bindingGeneration) {
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
            this.bindingGeneration = bindingGeneration;
        }
    }

    //? if <1.20
    @Desugar
    private record FailureKey(int dimensionId, long packedPosition, int handlerIdentity, String operation,
                              long bindingGeneration) {
    }
}
