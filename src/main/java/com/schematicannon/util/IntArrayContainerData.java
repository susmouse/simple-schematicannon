package com.schematicannon.util;

import net.minecraft.world.inventory.ContainerData;

/** Minimal {@link ContainerData} backed by an int array. */
public class IntArrayContainerData implements ContainerData {
    private final int[] values;

    public IntArrayContainerData(int size) {
        this.values = new int[size];
    }

    @Override
    public int get(int index) {
        return index >= 0 && index < values.length ? values[index] : 0;
    }

    @Override
    public void set(int index, int value) {
        if (index >= 0 && index < values.length) {
            values[index] = value;
        }
    }

    @Override
    public int getCount() {
        return values.length;
    }

    public int[] raw() {
        return values;
    }
}
