package com.redxela.model;

import com.redxela.model.enums.*;

/**
 * Representa la llegada de un lote de mercaderia desde el Area de Recepcion
 * hacia el Area de Control de Inventario a traves del bufer.
 *
 * @param batchId Identificador unico del lote.
 * @param product Producto recibido.
 * @param units Cantidad de unidades del lote.
 * @param arrivalTimestamp Marca de tiempo del arribo.
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
