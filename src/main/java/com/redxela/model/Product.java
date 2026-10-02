package com.redxela.model;

import com.redxela.model.enums.*;

import lombok.Getter;

/**
 * Representa un producto predefinido en el catalogo del centro de distribucion REDXela.
 */
@Getter
public class Product {
    private final String id;
    private final String name;
    private final MerchandiseType merchandiseType;
    // Cuantas unidades de este producto caben en un slot/bloque de almacen
    private final int unitsPerSlot;

    public Product(String id, String name, MerchandiseType merchandiseType, int unitsPerSlot) {
        this.id = id;
        this.name = name;
        this.merchandiseType = merchandiseType;
        this.unitsPerSlot = Math.max(1, unitsPerSlot);
    }

    @Override
    public String toString() {
        return id + " - " + name + " (" + merchandiseType.getDisplayName() + ")";
    }
}
