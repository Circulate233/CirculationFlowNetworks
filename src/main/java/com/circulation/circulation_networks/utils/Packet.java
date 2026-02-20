package com.circulation.circulation_networks.utils;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;

public interface Packet<T extends Packet<T>> extends IMessageHandler<T, IMessage>, IMessage {
}
