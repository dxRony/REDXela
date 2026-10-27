package com.redxela.model;

import com.redxela.model.enums.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * representa un pedido dentro del centro de distribucion
 * modela la informacion del cliente, nivel de servicio, mercancia,
 * tiempos de atencion y los recursos asignados/esperados durante su
 * procesamiento.
 */
@Getter
public class Order {

    // Identificador unico alfanumerico del pedido.
    private final String id;

    // Nombre de la cuenta del cliente que emitio la solicitud.
    private final String clientName;

    // Nivel de servicio contratado que determina el SLA objetivo de atencion.
    private final ServiceLevel serviceLevel;

    // Clasificacion de la mercancia que determina la combinacon y orden
    // de adquisicion de recursos.
    private final MerchandiseType merchandiseType;

    // Diccionario de productos solicitados y sus respectivas cantidades.
    private final Map<Product, Integer> items;

    // Total consolidado de articulos contenidos en el pedido
    private final int totalUnits;

    // Marca de tiempo de creacion del pedido en milisegundos.
    private final long createdAt;

    // Marca de tiempo en que el pedido fue retirado de la cola de espera para
    // asignacion de stock.
    @Setter
    private long scheduledAt;

    // Marca de tiempo en que el pedido inicio efectivamente la adquisicion
    // derecursos fisicos.
    @Setter
    private long processingStartedAt;

    // Marca de tiempo en que concluyeron todas las fases de empaque y despacho.
    @Setter
    private long completedAt;

    // Estado actual del ciclo de vida del pedido (volatile para lectura concurrente
    // segura).
    @Setter
    private volatile OrderStatus status;

    // Porcentaje de progreso de procesamiento (0 a 100%).
    private volatile int progressPercent;

    // Descripcion textual de la fase operativa en curso
    @Setter
    private volatile String currentPhaseDescription;

    // Conjunto de recursos fisicos que el pedido retiene actualmente
    private final Set<ResourceType> allocatedResources = ConcurrentHashMap.newKeySet();

    // Recurso fisico por el cual el pedido esta bloqueado actualmente
    @Setter
    private volatile ResourceType waitingForResource = null;

    // Bandera para forzar deliberadamente el conflicto cruzado de recursos y
    // simular un Deadlock.
    @Setter
    private volatile boolean forceDeadlockTest = false;

    // Hilo de ejecucion del pedido para soportar desalojo limpio (Preemption)
    @Setter
    private volatile Thread executionThread = null;

    // Bandera de desalojo forzoso por resolucion de deadlock
    @Setter
    private volatile boolean preempted = false;
    // Bandera que indica si el stock del pedido ya fue deducido del inventario global
    @Setter
    private volatile boolean stockDeducted = false;

    /**
     * Construye un nuevo pedido inicializando su estado en PENDIENTE.
     *
     * @param id              Identificador unico.
     * @param clientName      Nombre del cliente.
     * @param serviceLevel    Nivel de servicio (SLA).
     * @param merchandiseType Tipo de mercancia.
     * @param items           Mapa de productos y cantidades.
     */
    public Order(String id, String clientName, ServiceLevel serviceLevel,
            MerchandiseType merchandiseType, Map<Product, Integer> items) {
        this.id = id;
        this.clientName = clientName;
        this.serviceLevel = serviceLevel;
        this.merchandiseType = merchandiseType;
        this.items = new LinkedHashMap<>(items);

        int sum = 0;
        for (int qty : items.values()) {
            sum += qty;
        }
        this.totalUnits = Math.max(1, sum);

        this.createdAt = System.currentTimeMillis();
        this.status = OrderStatus.PENDIENTE;
        this.progressPercent = 0;
        this.currentPhaseDescription = "En espera de asignación";
    }

    /**
     * Calcula dinamicamente el puntaje de prioridad aplicando la tecnica de
     * Envejecimiento.
     * 
     * Conforme el pedido pasa mas tiempo en la cola, su puntaje se eleva para
     * evitar la inanicion
     * 
     * @param currentTime Marca de tiempo actual en milisegundos.
     * @return Puntaje numerico compuesto
     */
    public double calculateAgingPriority(long currentTime) {
        long waitingSeconds = (currentTime - createdAt) / 1000;
        // Prioridad base: Nivel 1 = 100 pts, Nivel 5 = 20 pts
        double baseScore = (6 - serviceLevel.getLevel()) * 20.0;
        // Bono dinamico por tiempo de espera: 1.5 puntos por cada segundo acumulado
        double agingBonus = waitingSeconds * 1.5;
        return baseScore + agingBonus;
    }

    /**
     * Registra un recurso como asignado al pedido bajo sincronizacion.
     * 
     * @param resourceType Recurso adquirido.
     */
    public synchronized void addAllocatedResource(ResourceType resourceType) {
        allocatedResources.add(resourceType);
    }

    /**
     * Elimina un recurso del conjunto retenido por el pedido.
     * 
     * @param resourceType Recurso liberado.
     */
    public synchronized void removeAllocatedResource(ResourceType resourceType) {
        allocatedResources.remove(resourceType);
    }

    // Limpia completamente todos los recursos retenidos
    public synchronized void clearAllocatedResources() {
        allocatedResources.clear();
    }

    /**
     * Retorna una vista inmutable de los recursos retenidos por el pedido.
     *
     * @return Conjunto de recursos asignados.
     */
    public Set<ResourceType> getAllocatedResources() {
        return Collections.unmodifiableSet(allocatedResources);
    }

    /**
     * Retorna una vista inmutable de los articulos del pedido.
     *
     * @return Mapa de articulos.
     */
    public Map<Product, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Actualiza el porcentaje de avance asegurando que permanezca en el rango
     * valido
     *
     * @param progressPercent Nuevo porcentaje de avance.
     */
    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.min(100, Math.max(0, progressPercent));
    }

    /**
     * Retorna el tiempo total transcurrido en la cola de espera en milisegundos.
     *
     * @return Milisegundos en espera.
     */
    public long getTotalWaitTimeMillis() {
        if (processingStartedAt > 0) {
            return processingStartedAt - createdAt;
        }
        return System.currentTimeMillis() - createdAt;
    }

    /**
     * Retorna el tiempo total transcurrido durante el procesamiento fisico en
     * estaciones de trabajo.
     *
     * @return Milisegundos de procesamiento activo.
     */
    public long getTotalProcessingTimeMillis() {
        if (completedAt > 0 && processingStartedAt > 0) {
            return completedAt - processingStartedAt;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "Pedido #" + id + " [" + serviceLevel.getDisplayName() + "] - " + merchandiseType.getDisplayName()
                + " (" + totalUnits + " arts.) - " + status;
    }
}
