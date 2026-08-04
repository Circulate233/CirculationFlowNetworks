package com.circulation.circulation_networks.api;

//~ mc_imports
import net.minecraft.util.math.BlockPos;
//~ if >=1.20 'net.minecraft.world.World' -> 'net.minecraft.world.level.Level' {
import net.minecraft.world.World;
//~}
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import java.util.Objects;

@ApiStatus.NonExtendable
public interface CFNBlockEntityEx {

    /** Saturated exponential stage retained after energy budget failures reach the backoff ceiling. */
    int SATURATED_ENERGY_THROTTLE_STAGE = 32;

    /** Maximum scheduled delay after saturated failures are distributed across neighboring ticks. */
    int MAX_ENERGY_THROTTLE_TIMER = SATURATED_ENERGY_THROTTLE_STAGE * 5 / 4;

    /** Base retry delay for a scoped block entity that currently has no available energy handler manager. */
    int ENERGY_DISCOVERY_THROTTLE_TIMER = 2000;

    /** Number of consecutive ticks across which unsupported-machine discovery retries are distributed. */
    int ENERGY_DISCOVERY_THROTTLE_SPREAD = 100;

    /** Smallest unsupported-machine discovery retry delay. */
    int MIN_ENERGY_DISCOVERY_THROTTLE_TIMER =
        ENERGY_DISCOVERY_THROTTLE_TIMER - ENERGY_DISCOVERY_THROTTLE_SPREAD / 2;

    /** Largest value stored in the shared throttle timer field. */
    int MAX_STORED_ENERGY_THROTTLE_TIMER =
        MIN_ENERGY_DISCOVERY_THROTTLE_TIMER + ENERGY_DISCOVERY_THROTTLE_SPREAD - 1;

    /**
     * Converts a Minecraft block entity received from an unmodified platform API into its CFN extension view.
     * Missing Mixin application is an installation error and therefore fails immediately.
     *
     * @param blockEntity native block entity returned by Minecraft
     * @return the same object through the CFN extension interface
     */
    static CFNBlockEntityEx cfn_cast(Object blockEntity) {
        if (blockEntity instanceof CFNBlockEntityEx extendedBlockEntity) {
            return extendedBlockEntity;
        }
        throw new IllegalStateException("Minecraft block entity does not implement CFNBlockEntityEx: "
            + Objects.requireNonNull(blockEntity, "blockEntity").getClass().getName());
    }

    /**
     * Returns the world currently owning this block entity, or {@code null} before attachment and after removal.
     */
    @Nullable
    //~ if >=1.20 'World ' -> 'Level ' {
    World cfn_getWorld();
    //~}

    /**
     * Returns the dimension identifier used by CFN's version-independent indexes.
     * The block entity must already be attached to a world.
     */
    int cfn_getDimensionId();

    /**
     * Returns the concrete native block-entity class name for diagnostics.
     */
    String cfn_getTypeName();

    /**
     * Returns the block position assigned by Minecraft to this block entity.
     */
    BlockPos cfn_getBlockPos();

    /**
     * Reports whether Minecraft has invalidated or removed this block entity.
     */
    boolean cfn_isRemoved();

    /**
     * Resolves the highest-priority energy manager currently available for this block entity.
     *
     * @param excludedManager manager that must not be selected during explicit owner invalidation
     * @return selected manager, or {@code null} when no registered manager is available
     */
    @Nullable
    IEnergyHandlerManager cfn_getEnergyManager(@Nullable IEnergyHandlerManager excludedManager);

    /**
     * Reports whether automatic machine registration is disabled for this block entity.
     */
    boolean cfn_isEnergyBlacklisted();

    /**
     * Reports whether supply nodes are forbidden from attaching to this block entity.
     */
    boolean cfn_isSupplyBlacklisted();

    /**
     * Binds a handler to this native block entity without exposing the platform block-entity type.
     */
    void cfn_bindEnergyHandler(IEnergyHandler handler, HandlerInvalidationSink invalidationSink);

    /**
     * Returns the manager-owned handler runtime currently attached to this block entity.
     */
    @Nullable
    EnergyMachineManager.MachineHandlerRuntime cfn_getMachineHandlerRuntime();

    /**
     * Installs a newly bound runtime. Installing over an existing runtime is an invariant violation.
     */
    void cfn_installMachineHandlerRuntime(EnergyMachineManager.MachineHandlerRuntime runtime);

    /**
     * Removes and returns the currently attached runtime.
     */
    @Nullable
    EnergyMachineManager.MachineHandlerRuntime cfn_removeMachineHandlerRuntime();

    /**
     * Returns the persistent transfer priority assigned to this machine.
     * The value is stored on the native block entity so route recreation, chunk reload and server restart preserve it.
     * Higher values are processed before lower values; the default is {@code 0}.
     */
    int cfn_getEnergyPriority();

    /**
     * Updates the persistent transfer priority assigned to this machine and marks the native block entity for saving.
     * Runtime participant indexes are updated separately by the machine registration owner.
     *
     * @param priority complete signed integer priority; higher values are processed first
     */
    void cfn_setEnergyPriority(int priority);

    /**
     * Restores the persistent transfer priority from Minecraft's authoritative block-entity loading path.
     * Unlike {@link #cfn_setEnergyPriority(int)}, this method must not mark the block entity dirty because loading an
     * unchanged chunk is not a state mutation.
     *
     * @param priority persisted signed integer priority, or {@code 0} when the tag is absent
     */
    void cfn_loadEnergyPriority(int priority);

    /**
     * Returns the active energy backoff marker. Budget backoff stores its remaining ticks; unsupported-machine
     * discovery stores its scheduled delay while the exact due tick is owned by the discovery time wheel.
     * A non-zero value suppresses all handler and budget reads for the current server tick.
     */
    int cfn_getEnergyThrottleTimer();

    /**
     * Sets the energy backoff marker. Budget backoff uses {@code 0..40}; unsupported-machine discovery uses
     * {@code 1950..2049}. Values between those ranges are reserved for internal scheduling.
     */
    void cfn_setEnergyThrottleTimer(int timer);

    /**
     * Returns the exponential backoff stage used to calculate the next delay.
     * Valid values are 0, 1, 2, 4, 8, 16 and 32; saturated scheduled delays
     * may independently range from 24 through 40 ticks.
     */
    int cfn_getEnergyLastThrottleTimer();

    /** Sets the exponential backoff stage retained after the previous failed sample. */
    void cfn_setEnergyLastThrottleTimer(int timer);

}
