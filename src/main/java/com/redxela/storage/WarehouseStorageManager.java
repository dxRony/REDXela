package com.redxela.storage;

import com.redxela.model.Product;
import com.redxela.model.WarehouseSlot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor del Almacén con Asignación Física No Contigua (análogo a la paginación de memoria).
 * Reparte los lotes de productos en distintos pasillos, racks y niveles según disponibilidad.
 */
public class WarehouseStorageManager {
    private final int totalAisles;
    private final int totalRacks;
    private final int totalLevels;
    private final int slotCapacity;

    private final List<WarehouseSlot> slots = new ArrayList<>();
    // Tabla de asignación por lote: LoteId -> Lista de slots donde quedó distribuido
    private final Map<String, List<WarehouseSlot>> batchAllocationTable = new ConcurrentHashMap<>();
    // Existencias totales por producto
    private final Map<String, Integer> stockMap = new ConcurrentHashMap<>();

    private final ReentrantLock storageLock = new ReentrantLock(true);

    public WarehouseStorageManager(int totalAisles, int totalRacks, int totalLevels, int slotCapacity) {
        this.totalAisles = totalAisles;
        this.totalRacks = totalRacks;
        this.totalLevels = totalLevels;
        this.slotCapacity = slotCapacity;

        // Inicializar la matriz física de slots
        for (int a = 1; a <= totalAisles; a++) {
            for (int r = 1; r <= totalRacks; r++) {
                for (int l = 1; l <= totalLevels; l++) {
                    String slotId = String.format("P%02d-R%02d-N%02d", a, r, l);
                    slots.add(new WarehouseSlot(slotId, a, r, l, slotCapacity));
                }
            }
        }
    }

    /**
     * Asigna un lote de mercadería entrante de forma NO CONTIGUA en el almacén.
     * Retorna la lista de slots donde quedó distribuido.
     */
    public List<WarehouseSlot> allocateGoods(String batchId, Product product, int unitsToStore) {
        storageLock.lock();
        try {
            List<WarehouseSlot> allocatedSlots = new ArrayList<>();
            int remainingUnits = unitsToStore;

            for (WarehouseSlot slot : slots) {
                if (remainingUnits <= 0) break;

                if (slot.isAvailable()) {
                    int canFit = Math.min(slot.getCapacity(), remainingUnits);
                    slot.assign(product.getId(), batchId, canFit);
                    allocatedSlots.add(slot);
                    remainingUnits -= canFit;
                }
            }

            if (!allocatedSlots.isEmpty()) {
                batchAllocationTable.put(batchId, allocatedSlots);
                stockMap.merge(product.getId(), (unitsToStore - remainingUnits), Integer::sum);
            }

            return allocatedSlots;
        } finally {
            storageLock.unlock();
        }
    }

    /**
     * Consume unidades físicas del almacén para despachar un pedido.
     * Libera los slots a medida que se vacían.
     */
    public boolean consumeStock(Product product, int unitsNeeded) {
        storageLock.lock();
        try {
            int currentStock = stockMap.getOrDefault(product.getId(), 0);
            if (currentStock < unitsNeeded) {
                return false; // Stock insuficiente
            }

            int toExtract = unitsNeeded;
            for (WarehouseSlot slot : slots) {
                if (toExtract <= 0) break;

                if (product.getId().equals(slot.getCurrentProductId())) {
                    int extracted = slot.release(toExtract);
                    toExtract -= extracted;
                }
            }

            stockMap.put(product.getId(), currentStock - unitsNeeded);
            return true;
        } finally {
            storageLock.unlock();
        }
    }

    public int getAvailableStock(Product product) {
        return stockMap.getOrDefault(product.getId(), 0);
    }

    public boolean hasStockForOrder(Map<Product, Integer> items) {
        storageLock.lock();
        try {
            for (Map.Entry<Product, Integer> entry : items.entrySet()) {
                if (getAvailableStock(entry.getKey()) < entry.getValue()) {
                    return false;
                }
            }
            return true;
        } finally {
            storageLock.unlock();
        }
    }

    public List<WarehouseSlot> getAllSlots() {
        storageLock.lock();
        try {
            return new ArrayList<>(slots);
        } finally {
            storageLock.unlock();
        }
    }

    public Map<String, List<WarehouseSlot>> getBatchAllocationTable() {
        return Collections.unmodifiableMap(batchAllocationTable);
    }

    public int getTotalSlots() {
        return slots.size();
    }

    public int getOccupiedSlotsCount() {
        storageLock.lock();
        try {
            return (int) slots.stream().filter(s -> !s.isAvailable()).count();
        } finally {
            storageLock.unlock();
        }
    }
}
