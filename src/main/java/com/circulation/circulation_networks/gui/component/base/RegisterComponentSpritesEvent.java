package com.circulation.circulation_networks.gui.component.base;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
//? if <1.20 {
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
//?} else if <1.21 {
/*import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
*///?} else {
/*import net.neoforged.bus.api.Event;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
*///?}

import java.util.List;

/**
 * Fired on the event bus before the {@link ComponentAtlas} begins stitching.
 * Subscribe to this event to register custom atlas sprites.
 *
 * <p>Registered names are collected during event dispatch and applied to the atlas
 * only by the atlas itself after the event completes. Manually constructing this
 * event and calling {@link #register} outside of the event bus has no effect on
 * the atlas.
 *
 * <p>All atlas sprites, including full-screen GUI backgrounds, must be located at
 * {@code assets/circulation_networks/textures/gui/component/<name>.png}.
 */
//~ if >=1.20 '@SideOnly(Side.CLIENT)' -> '@OnlyIn(Dist.CLIENT)' {
@SideOnly(Side.CLIENT)
//~}
public final class RegisterComponentSpritesEvent extends Event {

    private final ObjectList<String> sprites = new ObjectArrayList<>();

    /**
     * Registers an atlas sprite base-name (without {@code .png} extension) to be
     * included in the atlas.
     */
    public void register(String name) {
        sprites.add(name);
    }

    /**
     * Returns an unmodifiable view of all registered sprite names.
     */
    public List<String> getSprites() {
        return ObjectLists.unmodifiable(sprites);
    }
}
