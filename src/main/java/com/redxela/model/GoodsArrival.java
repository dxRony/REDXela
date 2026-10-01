package com.redxela.model;

/**
 * Mensaje enviado por el Área de Recepción al Área de Inventario.
 * Representa la llegada de un lote de mercadería transportado por camión.
 */
public record GoodsArrival(
        String batchId,
        Product product,
        int units,
        long arrivalTimestamp
) {
    @Override
    public String toString() {
        return "Lote " + batchId + ": " + units + "x " + product.getName();
    }
}
