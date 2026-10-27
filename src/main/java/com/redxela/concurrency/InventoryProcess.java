package com.redxela.concurrency;

import com.redxela.model.GoodsArrival;
import com.redxela.storage.WarehouseStorageManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proceso consumidor del Area de Control de Inventario.
 * Consume lotes del bufer de recepcion y distribuye fisicamente
 * las unidades en los slots del almacen actualizando el stock global.
 */
public class InventoryProcess implements Runnable {

    // Bufer sincronizado del cual se extraen los lotes.
    private final ReceptionBuffer buffer;

    // Gestor fisico del almacen donde se asignan los slots no contiguos.
    private final WarehouseStorageManager storageManager;

    // Bandera atomica que controla la terminacion limpia del bucle del hilo.
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Construye el proceso de inventario asociando el bufer y el gestor de almacenamiento.
     *
     * @param buffer         Bufer acotado compartido con Recepcion.
     * @param storageManager Gestor de almacenamiento fisico no contiguo.
     */
    public InventoryProcess(ReceptionBuffer buffer, WarehouseStorageManager storageManager) {
        this.buffer = buffer;
        this.storageManager = storageManager;
    }

    /**
     * Bucle principal de ejecucion del hilo consumidor.
     */
    @Override
    public void run() {
        running.set(true);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Consumidor: Se bloquea pasivamente en fullSlots hasta que Recepcion deposite mercaderia
                GoodsArrival arrival = buffer.consume();

                // Simulacion del tiempo fisico de inspeccion y desempaque del lote
                Thread.sleep(800);

                // Asignar los slots fisicos de forma NO CONTIGUA en el almacen (Paginacion)
                storageManager.allocateGoods(arrival.batchId(), arrival.product(), arrival.units());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Solicita la detencion ordenada del hilo de inventario.
     */
    public void stop() {
        running.set(false);
    }
}
