package com.circulation.circulation_networks.jei;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGhostIngredientHandler;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.util.List;

@SuppressWarnings("rawtypes")
public class CFNGuiHandler implements IAdvancedGuiHandler<CFNBaseGui>, IGhostIngredientHandler<CFNBaseGui> {

    @Override
    public @NotNull Class<CFNBaseGui> getGuiContainerClass() {
        return CFNBaseGui.class;
    }

    @Override
    public @NotNull List<Rectangle> getGuiExtraAreas(@NotNull CFNBaseGui guiContainer) {
        return getAreas(guiContainer);
    }

    public List<Rectangle> getAreas(CFNBaseGui<?> guiContainer) {
        return guiContainer.getGuiExtraAreas();
    }

    @Override
    public <I> @NotNull List<Target<I>> getTargets(@NotNull CFNBaseGui cfnBaseGui, @NotNull I i, boolean b) {
        return ObjectLists.emptyList();
    }

    @Override
    public void onComplete() {

    }
}
