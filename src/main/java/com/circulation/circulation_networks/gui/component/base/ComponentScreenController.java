package com.circulation.circulation_networks.gui.component.base;

import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ComponentScreenController {

    private static final Component[] EMPTY = new Component[0];

    private final Component[][] phaseComponents = new Component[RenderPhase.VALUES.length][];
    private Component[] allComponents = EMPTY;
    private Component[] allComponentsTopFirst = EMPTY;
    @Nullable
    private DraggableComponent dragTarget;

    {
        Arrays.fill(phaseComponents, EMPTY);
    }

    private static Component[] reverseCopy(Component[] source) {
        int length = source.length;
        if (length == 0) {
            return EMPTY;
        }

        Component[] reversed = new Component[length];
        for (int i = 0; i < length; i++) {
            reversed[i] = source[length - 1 - i];
        }
        return reversed;
    }

    private static boolean moveToEnd(Component[] source, Component component) {
        int index = -1;
        for (int i = 0; i < source.length; i++) {
            if (source[i] == component) {
                index = i;
                break;
            }
        }

        if (index < 0 || index == source.length - 1) {
            return false;
        }

        System.arraycopy(source, index + 1, source, index, source.length - index - 1);
        source[source.length - 1] = component;
        return true;
    }

    @Nullable
    private static DraggableComponent findDraggingComponent(Component[] nodes) {
        for (Component component : nodes) {
            if (component instanceof DraggableComponent draggableComponent && draggableComponent.isDragging()) {
                return draggableComponent;
            }
            DraggableComponent found = findDraggingComponent(component.getChildren());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static DraggableComponent findDraggingComponent(ObjectList<Component> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        for (Component component : nodes) {
            if (component instanceof DraggableComponent draggableComponent && draggableComponent.isDragging()) {
                return draggableComponent;
            }
            DraggableComponent found = findDraggingComponent(component.getChildren());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<LocalizedComponent> collectTopTooltip(Component[] components, int mouseX, int mouseY) {
        for (Component component : components) {
            if (!component.isVisible() || !component.contains(mouseX, mouseY)) {
                continue;
            }
            return component.collectTooltip(mouseX, mouseY);
        }
        return ObjectLists.emptyList();
    }

    @Nullable
    private static Component findTopComponentAt(Component[] components, int mouseX, int mouseY) {
        for (Component component : components) {
            if (!component.isVisible() || !component.contains(mouseX, mouseY)) {
                continue;
            }
            return findTopComponentAt(component, mouseX, mouseY);
        }
        return null;
    }

    @Nullable
    private static Component findTopComponentAt(Component component, int mouseX, int mouseY) {
        ObjectList<Component> children = component.getChildren();
        if (children.isEmpty()) {
            return component;
        }
        for (int i = children.size(); i-- > 0; ) {
            Component child = children.get(i);
            if (!child.isVisible() || !child.contains(mouseX, mouseY)) {
                continue;
            }
            return findTopComponentAt(child, mouseX, mouseY);
        }
        return component;
    }

    public void initializeComponents(Map<RenderPhase, List<Component>> phaseMap) {
        dragTarget = null;
        List<Component> all = new ObjectArrayList<>();
        RenderPhase[] phases = RenderPhase.VALUES;
        for (RenderPhase phase : phases) {
            List<Component> list = phaseMap.getOrDefault(phase, ObjectLists.emptyList());
            if (list.isEmpty()) {
                phaseComponents[phase.ordinal()] = EMPTY;
            } else {
                List<Component> sorted = new ObjectArrayList<>(list);
                sorted.sort(Comparator.comparingInt(Component::getZIndex));
                phaseComponents[phase.ordinal()] = sorted.toArray(new Component[0]);
                all.addAll(sorted);
            }
        }
        all.sort(Comparator.comparingInt(Component::getZIndex));
        allComponents = all.toArray(new Component[0]);
        allComponentsTopFirst = reverseCopy(allComponents);
    }

    public void handleActiveDrag(int mouseX, int mouseY) {
        if (dragTarget != null && dragTarget.isDragging()) {
            dragTarget.handleDrag(mouseX, mouseY);
        }
    }

    public void renderPhase(RenderPhase phase, int mouseX, int mouseY, float partialTicks) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        Component[] components = phaseComponents[phase.ordinal()];
        for (Component component : components) {
            component.renderComponent(mouseX, mouseY, partialTicks);
            component.renderComponentItems(mouseX, mouseY);
        }
        modelView.popMatrix();
    }

    public void updateComponents() {
        for (Component allComponent : allComponents) {
            allComponent.update();
        }
    }

    public void bringToFront(@Nullable Component component) {
        if (component == null) {
            return;
        }

        boolean changed = moveToEnd(allComponents, component);
        for (Component[] phaseArray : phaseComponents) {
            changed |= moveToEnd(phaseArray, component);
        }

        if (changed) {
            allComponentsTopFirst = reverseCopy(allComponents);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (Component component : allComponents) {
            component.dispatchGlobalMouseClicked(mouseX, mouseY, mouseButton);
        }

        for (Component component : allComponentsTopFirst) {
            if (!component.isVisible() || !component.contains(mouseX, mouseY)) {
                continue;
            }
            boolean handled = component.dispatchMouseClicked(mouseX, mouseY, mouseButton);
            if (handled) {
                dragTarget = findDraggingComponent(allComponents);
            }
            return handled;
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (dragTarget != null) {
            dragTarget.stopDrag(mouseX, mouseY);
            dragTarget = null;
            return true;
        }
        for (Component component : allComponentsTopFirst) {
            if (!component.isVisible() || !component.contains(mouseX, mouseY)) {
                continue;
            }
            return component.dispatchMouseReleased(mouseX, mouseY, state);
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        for (Component component : allComponentsTopFirst) {
            if (component.dispatchKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        for (Component component : allComponentsTopFirst) {
            if (!component.isVisible() || !component.contains(mouseX, mouseY)) {
                continue;
            }
            if (component.dispatchMouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    public List<String> collectTooltip(int mouseX, int mouseY) {
        if (allComponentsTopFirst.length == 0) {
            return ObjectLists.emptyList();
        }
        var list = collectTopTooltip(allComponentsTopFirst, mouseX, mouseY);
        if (list.isEmpty()) {
            return ObjectLists.emptyList();
        }
        var n = new ObjectArrayList<String>();
        for (var s : list) {
            n.add(s.get());
        }
        return n;
    }

    @Nullable
    public Component getTopComponentAt(int mouseX, int mouseY) {
        if (allComponentsTopFirst.length == 0) {
            return null;
        }
        return findTopComponentAt(allComponentsTopFirst, mouseX, mouseY);
    }

    public Component[] getAllComponents() {
        return allComponents;
    }
}
