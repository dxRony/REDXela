package com.redxela.concurrency;

import com.redxela.model.GoodsArrival;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bufer acotado que implementa el patron Productor-Consumidor.
 * Utiliza semaforos contadores (emptySlots, fullSlots) y un cerrojo mutex
 * para garantizar la sincronizacion segura entre recepcion e inventario.
 */
public class ReceptionBuffer {

    // Capacidad estricta maxima de almacenamiento de lotes dentro del bufer en
    // memoria.
    private final int bufferCapacity;

    // Cola FIFO en memoria donde residen los lotes pendientes de ser procesados por
    // Inventario.
    private final Queue<GoodsArrival> queue = new LinkedList<>();

    // Semaforo contador que cuantifica los espacios vacios disponibles. Se
    // inicializa con bufferCapacity.
    // Cada produce() lo decrementa con code acquire().
    private final Semaphore emptySlots;

    // Semaforo contador que cuantifica los lotes listos para ser consumidos. Se
    // inicializa en 0. Cada produce() lo incrementa
    // con release(), despertando al consumidor.
    private final Semaphore fullSlots;

    // Cerrojo de exclusion mutua justa (FIFO) que protege la manipulacion interna
    // de la cola.
    private final ReentrantLock mutex;

    /**
     * Construye un bufer acotado con la capacidad especificada.
     *
     * @param bufferCapacity Limite superior de lotes en transito simultaneos.
     */
    public ReceptionBuffer(int bufferCapacity) {
        this.bufferCapacity = Math.max(5, bufferCapacity);
        this.emptySlots = new Semaphore(this.bufferCapacity, true);
        this.fullSlots = new Semaphore(0, true);
        this.mutex = new ReentrantLock(true);
    }

    /**
     * Deposita un lote de mercaderia entrante en el bufer compartido.
     * 
     * Protocolo del Productor:
     * - emptySlots.acquire(): Si el bufer esta saturado (0 espacios
     * libres), el hilo de Recepcion se suspende de forma no ocupativa hasta que
     * Inventario libere un espacio.
     * - mutex.lock(): Adquiere el cerrojo de exclusion mutua.
     * - Inserta el lote en la cola.
     * - mutex.unlock(): Libera la region critica.
     * - fullSlots.release(): Senala que existe un nuevo lote disponible,
     * despertando al hilo consumidor.
     * 
     * @param arrival Lote de mercaderia recibido desde el camion.
     * @throws InterruptedException Si el hilo productor es interrumpido durante el
     *                              bloqueo.
     */
    public void produce(GoodsArrival arrival) throws InterruptedException {
        emptySlots.acquire(); // Espera pasiva si no hay espacio libre
        mutex.lock();
        try {
            queue.add(arrival);
        } finally {
            mutex.unlock();
        }
        fullSlots.release(); // Despierta al consumidor si estaba esperando
    }

    /**
     * Extrae un lote de mercaderia del bufer para su almacenamiento fisico.
     * 
     * Protocolo del Consumidor:
     * 
     * - fullSlots.acquire(): Si la cola esta vacia (0 elementos listos), el
     * hilo de Inventario se suspende hasta que Recepcion deposite nueva mercaderia.
     * - mutex.lock(): Adquiere el cerrojo de exclusion mutua.
     * - Extrae el lote en cabeza de cola.
     * - mutex.unlock(): Libera la region critica.
     * - emptySlots.release(): Senala la liberacion de un espacio,
     * despertando a productores bloqueados.
     * 
     * @return El lote de mercaderia extraido.
     * @throws InterruptedException Si el hilo consumidor es interrumpido durante el
     *                              bloqueo.
     */
    public GoodsArrival consume() throws InterruptedException {
        fullSlots.acquire(); // Espera pasiva si el bufer esta vacio
        GoodsArrival item;
        mutex.lock();
        try {
            item = queue.poll();
        } finally {
            mutex.unlock();
        }
        emptySlots.release(); // Senala espacio disponible al productor
        return item;
    }

    /**
     * Consulta el numero actual de lotes encolados dentro del bufer bajo exclusion
     * mutua.
     *
     * @return Cantidad de elementos en cola.
     */
    public int getCurrentSize() {
        mutex.lock();
        try {
            return queue.size();
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Retorna la capacidad total de diseno del bufer.
     *
     * @return Capacidad maxima de ranuras.
     */
    public int getBufferCapacity() {
        return bufferCapacity;
    }
}
