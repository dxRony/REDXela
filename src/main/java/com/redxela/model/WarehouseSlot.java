package com.redxela.model;

/**
 * Representa una ranura o ubicación física unitaria en el almacén de REDXela.
 * Modela la administración de almacenamiento no contiguo (similar a paginación de memoria).
 */
public class WarehouseSlot {
    private final String slotId; // ej. "P01-R02-N03"
    private final int aisle;     // Pasillo
    private final int rack;      // Estante / Bahía
    private final int level;     // Nivel (altura)

    private String currentProductId;
    private String currentBatchId; // Identificador del lote de mercadería
    private int storedUnits;
    private final int capacity;

    public WarehouseSlot(String slotId, int aisle, int rack, int level, int capacity) {
        this.slotId = slotId;
        this.aisle = aisle;
        this.rack = rack;
        this.level = level;
        this.capacity = capacity;
        this.currentProductId = null;
        this.currentBatchId = null;
        this.storedUnits = 0;
    }

    public synchronized boolean isAvailable() {
        return currentProductId == null || storedUnits == 0;
    }

    public synchronized boolean assign(String productId, String batchId, int units) {
        if (!isAvailable() && !productId.equals(this.currentProductId)) {
            return false;
        }
        int availableSpace = capacity - storedUnits;
        if (availableSpace <= 0) return false;

        int toStore = Math.min(availableSpace, units);
        this.currentProductId = productId;
        this.currentBatchId = batchId;
        this.storedUnits += toStore;
        return true;
    }

    public synchronized int release(int units) {
        if (storedUnits <= 0) return 0;
        int extracted = Math.min(units, storedUnits);
        storedUnits -= extracted;
        if (storedUnits == 0) {
            currentProductId = null;
            currentBatchId = null;
        }
        return extracted;
    }

    public synchronized void clear() {
        this.currentProductId = null;
        this.currentBatchId = null;
        this.storedUnits = 0;
    }

    public String getSlotId() {
        return slotId;
    }

    public int getAisle() {
        return aisle;
    }

    public int getRack() {
        return rack;
    }

    public int getLevel() {
        return level;
    }

    public synchronized String getCurrentProductId() {
        return currentProductId;
    }

    public synchronized String getCurrentBatchId() {
        return currentBatchId;
    }

    public synchronized int getStoredUnits() {
        return storedUnits;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public synchronized String toString() {
        if (isAvailable()) {
            return slotId + " [LIBRE]";
        }
        return slotId + " [" + currentProductId + " (" + storedUnits + "/" + capacity + ")]";
    }
}
