package com.redxela.model;

/**
 * Representa un producto predefinido en el catálogo del centro de distribución REDXela.
 */
public class Product {
    private final String id;
    private final String name;
    private final MerchandiseType merchandiseType;
    private final int unitsPerSlot; // Cuántas unidades de este producto caben en un slot/bloque de almacén

    public Product(String id, String name, MerchandiseType merchandiseType, int unitsPerSlot) {
        this.id = id;
        this.name = name;
        this.merchandiseType = merchandiseType;
        this.unitsPerSlot = Math.max(1, unitsPerSlot);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MerchandiseType getMerchandiseType() {
        return merchandiseType;
    }

    public int getUnitsPerSlot() {
        return unitsPerSlot;
    }

    @Override
    public String toString() {
        return id + " - " + name + " (" + merchandiseType.getDisplayName() + ")";
    }
}
