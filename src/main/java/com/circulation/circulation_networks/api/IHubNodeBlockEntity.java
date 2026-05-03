package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IHubNode;
//? if <1.21 {
import net.minecraftforge.items.IItemHandler;
//?} else {
/*import net.neoforged.neoforge.items.IItemHandler;
 *///?}

public interface IHubNodeBlockEntity extends INodeBlockEntity<IHubNode> {

    IItemHandler getPlugins();
}
