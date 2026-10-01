package com.redxela.concurrency;

import com.redxela.model.GoodsArrival;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffer Acotado (Bounded Buffer) para la comunicación Productor-Consumidor entre
 * el Área de Recepción (Productor) y el Área de Control de Inventario (Consumidor).
 * Implementado rigurosamente con Semáforos clásicos de Sistemas Operativos.
 */
public class ReceptionBuffer {
    private final int bufferCapacity;
    private final Queue<GoodsArrival> queue = new LinkedList<>();

    // Semáforos clásicos del problema Productor-Consumidor:
    private final Semaphore emptySlots; // Espacios vacíos disponibles en el buffer
    private final Semaphore fullSlots;  // Elementos listos para ser consumidos
    private final ReentrantLock mutex;  // Exclusión mutua para manipulación de la cola interna

    public ReceptionBuffer(int bufferCapacity) {
        this.bufferCapacity = Math.max(5, bufferCapacity);
        this.emptySlots = new Semaphore(this.bufferCapacity, true);
        this.fullSlots = new Semaphore(0, true);
        this.mutex = new ReentrantLock(true);
    }

    /**
     * Invocado por el hilo de Recepción para depositar un lote recibido.
     * Si el buffer está lleno, el hilo productor se bloquea hasta que Inventario libere espacio.
     */
    public void produce(GoodsArrival arrival) throws InterruptedException {
        emptySlots.acquire(); // Espera espacio vacío
        mutex.lock();
        try {
            queue.add(arrival);
        } finally {
            mutex.unlock();
        }
        fullSlots.release(); // Notifica que hay un nuevo elemento listo
    }

    /**
     * Invocado por el hilo de Inventario para extraer y procesar un lote.
     * Si el buffer está vacío, el hilo consumidor se bloquea hasta que Recepción deposite mercadería.
     */
    public GoodsArrival consume() throws InterruptedException {
        fullSlots.acquire(); // Espera elemento disponible
        GoodsArrival item;
        mutex.lock();
        try {
            item = queue.poll();
        } finally {
            mutex.unlock();
        }
        emptySlots.release(); // Notifica que se liberó un espacio en el buffer
        return item;
    }

    public int getCurrentSize() {
        mutex.lock();
        try {
            return queue.size();
        } finally {
            mutex.unlock();
        }
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }
}
