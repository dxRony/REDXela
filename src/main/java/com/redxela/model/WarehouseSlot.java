package com.redxela.model;

import lombok.Getter;

/**
 * Representa una ubicacion fisica individual en el almacen de REDXela (Pasillo, Rack y Nivel).
 * Modela el almacenamiento no contiguo para guardar lotes de productos.
 */
public class WarehouseSlot {

    // Identificador alfanumerico estandarizado del slot
    @Getter private final String slotId;

    // Numero de pasillo donde se localiza la ranura.
    @Getter private final int aisle;

    // Numero de estante o bahia vertical.
    @Getter private final int rack;

    // Nivel de altura dentro del estante (1, 2 o 3).
    @Getter private final int level;

    // Capacidad fisica maxima de unidades que caben en esta ranura.
    @Getter private final int capacity;

    // Codigo del producto que ocupa actualmente el slot (null libre).
    private String currentProductId;

    // Identificador del lote de mercaderia al cual pertenecen las unidades almacenadas.
    private String currentBatchId;

    // Conteo actual de unidades fisicas almacenadas en este slot.
    private int storedUnits;

    /**
     * Construye un slot fisico en las coordenadas dadas, inicialmente vacio.
     *
     * @param slotId Codigo identificador unico.
     * @param aisle Pasillo.
     * @param rack Estante.
     * @param level Nivel de altura.
     * @param capacity Capacidad maxima de unidades.
     */
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

    /**
     * Verifica de forma sincronizada si el slot se encuentra libre para ser asignado.
     *
     * @return true si no tiene producto asignado o si su existencia es 0.
     */
    public synchronized boolean isAvailable() {
        return currentProductId == null || storedUnits == 0;
    }

    /**
     * Asigna unidades de un producto y lote en este slot bajo sincronizacion.
     *
     * @param productId Codigo del producto.
     * @param batchId Identificador del lote.
     * @param units Cantidad de unidades a intentar almacenar.
     * @return true si se logro almacenar al menos una unidad. false si no hay espacio.
     */
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

    /**
     * Extrae unidades fisicas del slot. Si el conteo llega a 0, libera automaticamente la ranura.
     *
     * @param units Cantidad de unidades a retirar.
     * @return Unidades efectivamente extraidas.
     */
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

    /**
     * Vacia forzosamente el slot restableciendolo a estado libre.
     */
    public synchronized void clear() {
        this.currentProductId = null;
        this.currentBatchId = null;
        this.storedUnits = 0;
    }

    /**
     * Retorna el producto actualmente almacenado en el slot.
     *
     * @return Codigo del producto o  null
     */
    public synchronized String getCurrentProductId() {
        return currentProductId;
    }

    /**
     * Retorna el lote al que pertenecen las existencias del slot.
     *
     * @return ID del lote
     */
    public synchronized String getCurrentBatchId() {
        return currentBatchId;
    }

    /**
     * Retorna la cantidad de unidades actualmente almacenadas.
     *
     * @return Unidades presentes en la ranura.
     */
    public synchronized int getStoredUnits() {
        return storedUnits;
    }

    @Override
    public synchronized String toString() {
        if (isAvailable()) {
            return slotId + " [LIBRE]";
        }
        return slotId + " [" + currentProductId + " (" + storedUnits + "/" + capacity + ")]";
    }
}
