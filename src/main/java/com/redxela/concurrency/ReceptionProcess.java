package com.redxela.concurrency;

import com.redxela.model.GoodsArrival;
import com.redxela.model.Product;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Área de Recepción de Mercadería (Productor).
 * Registra la llegada de nuevos lotes y los deposita en el ReceptionBuffer.
 */
public class ReceptionProcess {
    private final ReceptionBuffer buffer;
    private final AtomicInteger batchCounter = new AtomicInteger(100);

    public ReceptionProcess(ReceptionBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Registra la llegada de un lote de mercancía desde un camión y lo envía al buffer acotado.
     */
    public void registerArrival(Product product, int units) throws InterruptedException {
        String batchId = "LOT-" + batchCounter.incrementAndGet();
        GoodsArrival arrival = new GoodsArrival(batchId, product, units, System.currentTimeMillis());
        buffer.produce(arrival);
    }
}
