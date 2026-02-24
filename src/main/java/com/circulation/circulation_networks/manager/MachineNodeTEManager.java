package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.utils.TileEntityLifeCycleEvent;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MachineNodeTEManager {

    public static final MachineNodeTEManager INSTANCE = new MachineNodeTEManager();

    private final ReferenceSet<IMachineNodeTileEntity> serverTe = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceSet<IMachineNodeTileEntity> clientTe = new ReferenceLinkedOpenHashSet<>();

    public void onTileEntityValidate(TileEntityLifeCycleEvent.Validate event) {
        if (event.getTileEntity() instanceof IMachineNodeTileEntity te)
            addTileEntity(te);
    }

    public void onTileEntityInvalidate(TileEntityLifeCycleEvent.Invalidate event) {
        if (event.getTileEntity() instanceof IMachineNodeTileEntity te)
            removeTileEntity(te);
    }

    public void addTileEntity(IMachineNodeTileEntity tileEntity) {
        if (tileEntity.getNode() != null) {
            serverTe.add(tileEntity);
        } else {
            clientTe.add(tileEntity);
        }
    }

    public void removeTileEntity(IMachineNodeTileEntity tileEntity) {
        if (tileEntity.getNode() != null) {
            serverTe.remove(tileEntity);
        } else {
            clientTe.remove(tileEntity);
        }
    }

    @SideOnly(Side.CLIENT)
    public void onClientTick() {
        for (var tileEntity : clientTe) {
            tileEntity.clientUpdate();
        }
    }

    public void onServerTick() {
        for (var tileEntity : serverTe) {
            tileEntity.serverUpdate();
        }
    }

    public void clear() {
        serverTe.clear();
    }
}