package net.runelite.client.plugins.microbot.util.containers;
import java.util.LinkedList;

public class FixedSizeQueue<E> {
    private final int maxSize;
    private final LinkedList<E> list = new LinkedList<>();

    public FixedSizeQueue(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be greater than 0");
        }
        this.maxSize = maxSize;
    }

    public void add(E element) {
        if (list.size() == maxSize) {
            list.removeFirst();
        }
        list.addLast(element);
    }

    public LinkedList<E> getAll() {
        return list;
    }

    public E get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public boolean contains(E element) {
        return list.contains(element);
    }

    @Override
    public String toString() {
        return list.toString();
    }
}