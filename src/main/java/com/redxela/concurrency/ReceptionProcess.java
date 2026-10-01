package com.redxela.concurrency;

import com.redxela.model.GoodsArrival;
import com.redxela.model.Product;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proceso productor del Area de Recepcion de Mercaderia.
 * Registra el arribo de camiones y deposita los lotes en el bufer acotado
 * para que sean procesados por el equipo de inventario.
 */
public class ReceptionProcess {

    // Bufer acotado donde se encolan los lotes recibidos.
    private final ReceptionBuffer buffer;

    // Contador atomico para generar identificadores unicos y secuenciales de lote
    // (thread-safe).
    private final AtomicInteger batchCounter = new AtomicInteger(100);

    /**
     * Construye el proceso de recepcion vinculado al bufer acotado del centro.
     *
     * @param buffer Bufer donde se depositaran los lotes entrantes.
     */
    public ReceptionProcess(ReceptionBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Registra la llegada fisica de un lote de mercancia desde un camion y lo
     * deposita
     * en el bufer acotado para su posterior procesamiento por parte de Inventario.
     *
     * @param product Producto recibido del catalogo.
     * @param units   Cantidad de unidades fisicas contenidas en el lote.
     * @throws InterruptedException Si el hilo se bloquea en emptySlots y es
     *                              interrumpido.
     */
    public void registerArrival(Product product, int units) throws InterruptedException {
        String batchId = "LOT-" + batchCounter.incrementAndGet();
        GoodsArrival arrival = new GoodsArrival(batchId, product, units, System.currentTimeMillis());
        buffer.produce(arrival);
    }
}
