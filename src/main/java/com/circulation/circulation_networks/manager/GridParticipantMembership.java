package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Participant-owned location state for persistent role indexes. It replaces
 * per-index reference maps: cold-path remove and move operations locate the
 * grid bucket directly from this object. A future channel index may use the
 * independent channel bucket slot without disturbing grid membership.
 */
public final class GridParticipantMembership {

    @Nullable
    private GridParticipantIndex owner;
    @Nullable
    private PriorityRoleIndex ownerRoleIndex;
    @Nullable
    private PriorityRoleIndex.Bucket gridBucket;
    @Nullable
    private PriorityRoleIndex.Bucket channelBucket;
    @Nullable
    private IEnergyHandler.EnergyType role;
    private int priority;
    @Nullable
    private ChannelParticipantIndex channelOwner;
    @Nullable
    private UUID channelId;
    @Nullable
    private PriorityRoleIndex channelRoleIndex;
    @Nullable
    private IEnergyHandler.EnergyType channelRole;
    private int channelPriority;

    /**
     * Returns whether the participant currently belongs to a grid index.
     *
     * @return {@code true} when grid membership is active
     */
    public boolean isBound() {
        return owner != null;
    }

    /**
     * Returns the owning grid index.
     *
     * @return owner index, or {@code null} when unbound
     */
    @Nullable
    public GridParticipantIndex owner() {
        return owner;
    }

    /**
     * Returns the active transfer role.
     *
     * @return role, or {@code null} when unbound
     */
    @Nullable
    public IEnergyHandler.EnergyType role() {
        return role;
    }

    /**
     * Returns the active full-range integer priority.
     *
     * @return participant priority
     * @throws IllegalStateException when the participant is unbound
     */
    public int priority() {
        if (owner == null) {
            throw new IllegalStateException("Participant is not registered in a grid index");
        }
        return priority;
    }

    /**
     * Returns the grid priority bucket containing this participant.
     *
     * @return grid bucket, or {@code null} when unbound
     */
    @Nullable
    public PriorityRoleIndex.Bucket gridBucket() {
        return gridBucket;
    }

    /**
     * Returns the optional channel priority bucket used by a future merged
     * channel index.
     *
     * @return channel bucket, or {@code null} when absent
     */
    @Nullable
    public PriorityRoleIndex.Bucket channelBucket() {
        return channelBucket;
    }

    /**
     * Returns whether the participant currently belongs to a channel index.
     *
     * @return {@code true} when channel membership is active
     */
    public boolean isChannelBound() {
        return channelOwner != null;
    }

    /**
     * Returns the channel identifier owning this participant's channel bucket.
     *
     * @return channel identifier, or {@code null} when unbound
     */
    @Nullable
    public UUID channelId() {
        return channelId;
    }

    /**
     * Returns the participant's role within its channel index.
     *
     * @return channel role, or {@code null} when unbound
     */
    @Nullable
    public IEnergyHandler.EnergyType channelRole() {
        return channelRole;
    }

    /**
     * Returns the participant's channel priority.
     *
     * @return channel priority
     * @throws IllegalStateException when channel membership is absent
     */
    public int channelPriority() {
        if (channelOwner == null) {
            throw new IllegalStateException("Participant is not registered in a channel index");
        }
        return channelPriority;
    }

    void bind(GridParticipantIndex owner,
              PriorityRoleIndex ownerRoleIndex,
              PriorityRoleIndex.Bucket gridBucket,
              IEnergyHandler.EnergyType role,
              int priority) {
        if (this.owner != null) {
            throw new IllegalStateException("Participant already has grid membership");
        }
        this.owner = owner;
        this.ownerRoleIndex = ownerRoleIndex;
        this.gridBucket = gridBucket;
        this.role = role;
        this.priority = priority;
    }

    void move(PriorityRoleIndex ownerRoleIndex,
              PriorityRoleIndex.Bucket gridBucket,
              IEnergyHandler.EnergyType role,
              int priority) {
        if (owner == null) {
            throw new IllegalStateException("Participant has no grid membership to move");
        }
        this.ownerRoleIndex = ownerRoleIndex;
        this.gridBucket = gridBucket;
        this.role = role;
        this.priority = priority;
    }

    void clear(GridParticipantIndex expectedOwner) {
        if (owner != expectedOwner) {
            throw new IllegalStateException("Participant belongs to a different grid index");
        }
        owner = null;
        ownerRoleIndex = null;
        gridBucket = null;
        role = null;
        priority = 0;
    }

    void clearFromRoleIndex(PriorityRoleIndex expectedRoleIndex) {
        if (ownerRoleIndex != expectedRoleIndex) {
            throw new IllegalStateException("Participant belongs to a different role index");
        }
        owner = null;
        ownerRoleIndex = null;
        gridBucket = null;
        role = null;
        priority = 0;
    }

    boolean isBound(ParticipantMembershipScope scope) {
        return scope == ParticipantMembershipScope.GRID ? isBound() : isChannelBound();
    }

    PriorityRoleIndex ownerRoleIndex(ParticipantMembershipScope scope) {
        if (scope == ParticipantMembershipScope.GRID) {
            return ownerRoleIndex();
        }
        if (channelRoleIndex == null) {
            throw new IllegalStateException("Participant is not registered in a channel role index");
        }
        return channelRoleIndex;
    }

    PriorityRoleIndex.Bucket bucket(ParticipantMembershipScope scope) {
        return scope == ParticipantMembershipScope.GRID ? gridBucket : channelBucket;
    }

    void clearFromRoleIndex(ParticipantMembershipScope scope, PriorityRoleIndex expectedRoleIndex) {
        if (scope == ParticipantMembershipScope.GRID) {
            clearFromRoleIndex(expectedRoleIndex);
            return;
        }
        if (channelRoleIndex != expectedRoleIndex) {
            throw new IllegalStateException("Participant belongs to a different channel role index");
        }
        channelOwner = null;
        channelId = null;
        channelRoleIndex = null;
        channelBucket = null;
        channelRole = null;
        channelPriority = 0;
    }

    void bindChannel(ChannelParticipantIndex owner,
                     UUID channelId,
                     PriorityRoleIndex ownerRoleIndex,
                     PriorityRoleIndex.Bucket channelBucket,
                     IEnergyHandler.EnergyType role,
                     int priority) {
        if (channelOwner != null) {
            throw new IllegalStateException("Participant already has channel membership");
        }
        this.channelOwner = owner;
        this.channelId = channelId;
        this.channelRoleIndex = ownerRoleIndex;
        this.channelBucket = channelBucket;
        this.channelRole = role;
        this.channelPriority = priority;
    }

    void moveChannel(PriorityRoleIndex ownerRoleIndex,
                     PriorityRoleIndex.Bucket channelBucket,
                     IEnergyHandler.EnergyType role,
                     int priority) {
        if (channelOwner == null) {
            throw new IllegalStateException("Participant has no channel membership to move");
        }
        this.channelRoleIndex = ownerRoleIndex;
        this.channelBucket = channelBucket;
        this.channelRole = role;
        this.channelPriority = priority;
    }

    void clearChannel(ChannelParticipantIndex expectedOwner, UUID expectedChannelId) {
        if (channelOwner != expectedOwner || !expectedChannelId.equals(channelId)) {
            throw new IllegalStateException("Participant belongs to a different channel index");
        }
        channelOwner = null;
        channelId = null;
        channelRoleIndex = null;
        channelBucket = null;
        channelRole = null;
        channelPriority = 0;
    }

    void setChannelBucket(@Nullable PriorityRoleIndex.Bucket channelBucket) {
        this.channelBucket = channelBucket;
    }

    PriorityRoleIndex ownerRoleIndex() {
        if (ownerRoleIndex == null) {
            throw new IllegalStateException("Participant is not registered in a role index");
        }
        return ownerRoleIndex;
    }
}
