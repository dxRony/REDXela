package com.redxela.concurrency;

import com.redxela.model.GoodsArrival;
import com.redxela.model.WarehouseSlot;
import com.redxela.storage.WarehouseStorageManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hilo independiente del Área de Control de Inventario (Consumidor).
 * Extrae los lotes depositados en el ReceptionBuffer y los ubica de forma no contigua
 * en el almacén físico, actualizando existencias para los pedidos en espera.
 */
public class InventoryProcess implements Runnable {
    private final ReceptionBuffer buffer;
    private final WarehouseStorageManager storageManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<InventoryListener> listeners = new CopyOnWriteArrayList<>();

    public interface InventoryListener {
        void onStockStored(GoodsArrival arrival, List<WarehouseSlot> allocatedSlots);
    }

    public InventoryProcess(ReceptionBuffer buffer, WarehouseStorageManager storageManager) {
        this.buffer = buffer;
        this.storageManager = storageManager;
    }

    public void addListener(InventoryListener listener) {
        listeners.add(listener);
    }

    @Override
    public void run() {
        running.set(true);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Consumidor: Se bloquea en el semáforo fullSlots hasta que Recepción deposite algo
                GoodsArrival arrival = buffer.consume();

                // Simular el tiempo de procesamiento y registro en inventario
                Thread.sleep(800);

                // Asignar los slots físicos de forma NO CONTIGUA en el almacén
                List<WarehouseSlot> allocatedSlots = storageManager.allocateGoods(
                        arrival.batchId(), arrival.product(), arrival.units()
                );

                // Notificar a observadores (para actualizar UI de almacén)
                for (InventoryListener listener : listeners) {
                    listener.onStockStored(arrival, allocatedSlots);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running.set(false);
    }
}
