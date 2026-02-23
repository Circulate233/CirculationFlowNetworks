package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.packets.ContainerProgressBar;
import com.circulation.circulation_networks.packets.ContainerValueConfig;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Objects;

public class SyncData {
    private final CFNBaseContainer source;
    private final Field field;
    @Getter
    private final int channel;
    private Object clientVersion = null;

    public SyncData(CFNBaseContainer container, Field field, GuiSync annotation) {
        this.source = container;
        this.field = field;
        this.channel = annotation.value();
    }

    public void tick(IContainerListener c) {
        try {
            Object val = this.field.get(this.source);
            if (val != null && this.clientVersion == null) {
                this.send(c, val);
            } else if (!Objects.equals(val, this.clientVersion)) {
                this.send(c, val);
            }
        } catch (IllegalArgumentException | IOException | IllegalAccessException e) {
            CirculationFlowNetworks.LOGGER.debug(e);
        }

    }

    private void send(IContainerListener o, Object val) throws IOException {
        if (val instanceof String s) {
            if (o instanceof EntityPlayerMP) {
                CirculationFlowNetworks.NET_CHANNEL.sendTo(new ContainerValueConfig((short) this.channel, s), (EntityPlayerMP) o);
            }
        } else if (this.field.getType().isEnum()) {
            o.sendWindowProperty(this.source, this.channel, ((Enum) val).ordinal());
        } else if (!(val instanceof Long) && val.getClass() != Long.TYPE) {
            if (!(val instanceof Boolean) && val.getClass() != Boolean.TYPE) {
                o.sendWindowProperty(this.source, this.channel, (Integer) val);
            } else {
                o.sendWindowProperty(this.source, this.channel, (Boolean) val ? 1 : 0);
            }
        } else if (o instanceof EntityPlayerMP) {
            CirculationFlowNetworks.NET_CHANNEL.sendTo(new ContainerProgressBar((short) this.channel, (Long) val), (EntityPlayerMP) o);
        }

        this.clientVersion = val;
    }

    public void update(Object val) {
        try {
            if (val instanceof String) {
                this.updateString((String) val);
            } else {
                this.updateValue((Long) val);
            }
        } catch (IllegalArgumentException e) {
            CirculationFlowNetworks.LOGGER.debug(e);
        }

    }

    private void updateString(String val) {
        try {
            this.field.set(this.source, val);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            CirculationFlowNetworks.LOGGER.debug(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> void updateValue(long val) {
        try {
            if (this.field.getType().isEnum()) {
                for (Enum e : EnumSet.allOf((Class<E>) this.field.getType())) {
                    if ((long) e.ordinal() == val) {
                        this.field.set(this.source, e);
                        break;
                    }
                }
            } else if (this.field.getType().equals(Integer.TYPE)) {
                this.field.set(this.source, (int) val);
            } else if (this.field.getType().equals(Long.TYPE)) {
                this.field.set(this.source, val);
            } else if (this.field.getType().equals(Boolean.TYPE)) {
                this.field.set(this.source, val == 1L);
            } else if (this.field.getType().equals(Integer.class)) {
                this.field.set(this.source, (int) val);
            } else if (this.field.getType().equals(Long.class)) {
                this.field.set(this.source, val);
            } else if (this.field.getType().equals(Boolean.class)) {
                this.field.set(this.source, val == 1L);
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            CirculationFlowNetworks.LOGGER.debug(e);
        }

    }
}
