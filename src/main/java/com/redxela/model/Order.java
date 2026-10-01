package com.redxela.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representa un pedido dentro del Centro de Distribución REDXela.
 */
public class Order {
    private final String id;
    private final String clientName;
    private final ServiceLevel serviceLevel;
    private final MerchandiseType merchandiseType;
    private final Map<Product, Integer> items; // Producto -> Cantidad solicitada
    private final int totalUnits;

    // Métricas de tiempo (milisegundos)
    private final long createdAt;
    private long scheduledAt;
    private long processingStartedAt;
    private long completedAt;

    // Estado concurrente
    private volatile OrderStatus status;
    private volatile int progressPercent; // 0 a 100
    private volatile String currentPhaseDescription;

    // Recursos retenidos y recurso esperado (para el Grafo de Asignación de Recursos / RAG)
    private final Set<ResourceType> allocatedResources = ConcurrentHashMap.newKeySet();
    private volatile ResourceType waitingForResource = null;

    // Para forzar deadlock deliberadamente en pruebas
    private volatile boolean forceDeadlockTest = false;

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

    // Calcula prioridad dinámica considerando envejecimiento (Aging) para evitar inanición
    public double calculateAgingPriority(long currentTime) {
        long waitingSeconds = (currentTime - createdAt) / 1000;
        // Prioridad base: Nivel 1 = 100 pts, Nivel 5 = 20 pts
        double baseScore = (6 - serviceLevel.getLevel()) * 20.0;
        // Bono por tiempo de espera: 1 punto por cada segundo de espera
        double agingBonus = waitingSeconds * 1.5;
        return baseScore + agingBonus;
    }

    public synchronized void addAllocatedResource(ResourceType resourceType) {
        allocatedResources.add(resourceType);
    }

    public synchronized void removeAllocatedResource(ResourceType resourceType) {
        allocatedResources.remove(resourceType);
    }

    public synchronized void clearAllocatedResources() {
        allocatedResources.clear();
    }

    public Set<ResourceType> getAllocatedResources() {
        return Collections.unmodifiableSet(allocatedResources);
    }

    public ResourceType getWaitingForResource() {
        return waitingForResource;
    }

    public void setWaitingForResource(ResourceType waitingForResource) {
        this.waitingForResource = waitingForResource;
    }

    public String getId() {
        return id;
    }

    public String getClientName() {
        return clientName;
    }

    public ServiceLevel getServiceLevel() {
        return serviceLevel;
    }

    public MerchandiseType getMerchandiseType() {
        return merchandiseType;
    }

    public Map<Product, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(long scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public long getProcessingStartedAt() {
        return processingStartedAt;
    }

    public void setProcessingStartedAt(long processingStartedAt) {
        this.processingStartedAt = processingStartedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.min(100, Math.max(0, progressPercent));
    }

    public String getCurrentPhaseDescription() {
        return currentPhaseDescription;
    }

    public void setCurrentPhaseDescription(String currentPhaseDescription) {
        this.currentPhaseDescription = currentPhaseDescription;
    }

    public boolean isForceDeadlockTest() {
        return forceDeadlockTest;
    }

    public void setForceDeadlockTest(boolean forceDeadlockTest) {
        this.forceDeadlockTest = forceDeadlockTest;
    }

    public long getTotalWaitTimeMillis() {
        if (processingStartedAt > 0) {
            return processingStartedAt - createdAt;
        }
        return System.currentTimeMillis() - createdAt;
    }

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
