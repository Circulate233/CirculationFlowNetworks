package com.circulation.circulation_networks.jei;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CFNGuiHandler<T extends CFNBaseGui<?>> implements IGuiContainerHandler<T>, IGhostIngredientHandler<T> {

    @Override
    public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull T containerScreen) {
        return containerScreen.getGuiExtraAreas();
    }

    @Override
    public <I> @NotNull List<Target<I>> getTargetsTyped(@NotNull T gui, @NotNull ITypedIngredient<I> ingredient, boolean doStart) {
        return ObjectLists.emptyList();
    }

    @Override
    public void onComplete() {
    }
}
