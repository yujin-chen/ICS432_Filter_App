package ics432.imgapp;

import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * A bounded buffer implementation that can be used to communicate between threads
 * @param <T> The type of the elements in the buffer
 */
public class BoundedBuffer<T> {
    private final ArrayBlockingQueue<T> buffer;

    /**
     * Constructor
     * @param capacity The capacity of the buffer
     */
    public BoundedBuffer(int capacity) {
        this.buffer = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Put an item into the buffer
     * @param item The item to put into the buffer
     * @throws InterruptedException If the thread is interrupted
     * The producer.
     */
    public void put(T item) throws InterruptedException {
        buffer.put(item); // Blocks if the buffer is full
    }

    /**
     * Take an item from the buffer
     * @return The item taken from the buffer
     * @throws InterruptedException If the thread is interrupted
     * The consumer.
     */
    public T take() throws InterruptedException {
        return buffer.take(); // Blocks if the buffer is empty
    }


}

