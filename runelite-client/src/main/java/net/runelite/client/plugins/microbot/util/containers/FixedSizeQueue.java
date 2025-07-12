package net.runelite.client.plugins.microbot.util.containers;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;

public class FixedSizeQueue<E> extends LinkedList<E> {
    private final int maxSize;

    public FixedSizeQueue(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be greater than 0");
        }
        this.maxSize = maxSize;
    }

    private void checkSize() {
        if (super.size() < maxSize) return;
        super.removeFirst();
    }

    @Override
    public boolean add(E element) {
        checkSize();
        return super.add(element);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        throw new UnsupportedOperationException("addAll is currently not supported");
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        throw new UnsupportedOperationException("addAll is currently not supported");
    }

    @Override
    public void add(int index, E element) {
        if (super.size() >= maxSize) {
            super.removeFirst();
            index = Math.max(0, index-1);
        }
        super.add(index, element);
    }

    @Override
    public void addFirst(E element) {
        checkSize();
        super.addFirst(element);
    }

    @Override
    public void addLast(E element) {
        checkSize();
        super.addLast(element);
    }

    @Deprecated
    public LinkedList<E> getAll() {
        return this;
    }
}