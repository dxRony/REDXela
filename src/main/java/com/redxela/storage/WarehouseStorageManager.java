package com.redxela.storage;

import com.redxela.model.Product;
import com.redxela.model.WarehouseSlot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor de almacenamiento fisico no contiguo inspirado en paginacion de memoria.
 * Administra los slots del almacen (marcos) y mantiene una tabla de asignacion
 * que vincula cada lote de productos con sus ubicaciones fisicas distribuidas.
 */
public class WarehouseStorageManager {

    // Matriz lineal que contiene todas las ranuras o slots fisicos del almacen.
    private final List<WarehouseSlot> slots = new ArrayList<>();

    // Tabla de asignacion no contigua (analoga a la Tabla de Paginas): Mapea el identificador de un lote
    // entrante con la lista de slots dispersos donde quedo almacenado.
    private final Map<String, List<WarehouseSlot>> batchAllocationTable = new ConcurrentHashMap<>();

    // Mapeo de inventario consolidado: Codigo de Producto -> Unidades totales disponibles en el centro.
    private final Map<String, Integer> stockMap = new ConcurrentHashMap<>();

    // Cerrojo de exclusion mutua para operaciones atomicas de asignacion y consumo de almacenamiento.
    private final ReentrantLock storageLock = new ReentrantLock(true);

    /**
     * Inicializa la topologia tridimensional fisica del almacen.
     *
     * @param totalAisles Cantidad total de pasillos (eje X).
     * @param totalRacks Cantidad de estantes por pasillo (eje Y).
     * @param totalLevels Cantidad de niveles de altura por estante (eje Z).
     * @param slotCapacity Capacidad maxima de unidades que caben en un solo slot.
     */
    public WarehouseStorageManager(int totalAisles, int totalRacks, int totalLevels, int slotCapacity) {
        // Inicializar la matriz fisica de slots
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
     * Asigna un lote de mercaderia entrante de forma NO CONTIGUA en los slots libres disponibles.
     * 
     * Las unidades se fragmentan y llenan los slots disponibles independientemente de si estan
     * contiguos o repartidos en diferentes pasillos y alturas.
     * 
     * @param batchId Identificador unico del lote.
     * @param product Producto que se almacena.
     * @param unitsToStore Cantidad de unidades a guardar.
     * @return Lista de WarehouseSlot donde quedo repartido el lote.
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
     * Consume y extrae unidades fisicas del almacen para despachar un pedido completado.
     * 
     * Libera las ranuras a medida que se vacian a 0 unidades, retornandolas al estado libre.
     * 
     * @param product Producto a extraer.
     * @param unitsNeeded Cantidad de unidades requeridas por el pedido.
     * @return true si se extrajo con exito, o false si no habia suficiente stock.
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

    /**
     * Consulta las existencias disponibles de un producto especifico en el inventario.
     *
     * @param product Producto a consultar.
     * @return Unidades totales almacenadas.
     */
    public int getAvailableStock(Product product) {
        return stockMap.getOrDefault(product.getId(), 0);
    }

    /**
     * Verifica bajo exclusion mutua si el almacen cuenta con stock suficiente para satisfacer
     * todos los articulos solicitados en un pedido.
     *
     * @param items Mapeo de Producto -> Unidades solicitadas.
     * @return {@code true} si todos los articulos estan disponibles, false en caso contrario.
     */
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

    /**
     * Retorna una instantanea defensiva de todos los slots del almacen para renderizado en la UI.
     *
     * @return Copia de la lista de ranuras fisicas.
     */
    public List<WarehouseSlot> getAllSlots() {
        storageLock.lock();
        try {
            return new ArrayList<>(slots);
        } finally {
            storageLock.unlock();
        }
    }

    /**
     * Retorna una vista de solo lectura de la tabla de asignacion por lote.
     *
     * @return Mapeo LoteId -> Lista de ranuras.
     */
    public Map<String, List<WarehouseSlot>> getBatchAllocationTable() {
        return Collections.unmodifiableMap(batchAllocationTable);
    }

    /**
     * Retorna la capacidad total de ranuras del almacen.
     *
     * @return Numero total de slots.
     */
    public int getTotalSlots() {
        return slots.size();
    }

    /**
     * Retorna el numero actual de ranuras ocupadas con mercaderia.
     *
     * @return Conteo de slots no disponibles.
     */
    public int getOccupiedSlotsCount() {
        storageLock.lock();
        try {
            return (int) slots.stream().filter(s -> !s.isAvailable()).count();
        } finally {
            storageLock.unlock();
        }
    }
}
