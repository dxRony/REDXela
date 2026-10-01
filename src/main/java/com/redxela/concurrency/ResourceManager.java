package com.redxela.concurrency;

import com.redxela.model.Order;
import com.redxela.model.ResourceType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Administrador de Recursos Compartidos del Centro de Distribución.
 * Utiliza semáforos para controlar la exclusión mutua y capacidad de cada recurso.
 * Mantiene el estado para el Grafo de Asignación de Recursos (RAG).
 */
public class ResourceManager {
    // Semáforos para cada recurso físico (inician con la capacidad total disponible)
    private final Map<ResourceType, Semaphore> semaphores = new EnumMap<>(ResourceType.class);

    // Rastreo de asignación (Grafo de Asignación de Recursos / RAG)
    // Recurso -> Lista de pedidos que lo tienen asignado actualmente
    private final Map<ResourceType, Set<Order>> resourceHolders = new EnumMap<>(ResourceType.class);
    // Pedido -> Recurso que está esperando actualmente (arista de solicitud P -> R)
    private final Map<Order, ResourceType> orderWaitingFor = new ConcurrentHashMap<>();

    // Lock de sincronización para operaciones sobre el grafo RAG
    private final ReentrantLock ragLock = new ReentrantLock();

    public ResourceManager() {
        for (ResourceType type : ResourceType.values()) {
            semaphores.put(type, new Semaphore(type.getTotalCapacity(), true)); // Semáforo justo (FIFO)
            resourceHolders.put(type, ConcurrentHashMap.newKeySet());
        }
    }

    /**
     * Registra manualmente la espera de un recurso (útil para pruebas y control de grafo RAG).
     */
    public void registerWaiting(Order order, ResourceType resource) {
        ragLock.lock();
        try {
            order.setWaitingForResource(resource);
            orderWaitingFor.put(order, resource);
        } finally {
            ragLock.unlock();
        }
    }

    /**
     * Intenta adquirir un recurso. Si no está disponible, el hilo se bloquea hasta obtenerlo.
     * Actualiza el Grafo de Asignación de Recursos para detección de deadlocks.
     */
    public void acquireResource(Order order, ResourceType resource, int count) throws InterruptedException {
        // Registrar arista de solicitud en el RAG (Order -> Resource)
        ragLock.lock();
        try {
            order.setWaitingForResource(resource);
            orderWaitingFor.put(order, resource);
        } finally {
            ragLock.unlock();
        }

        // Adquirir los permisos en el semáforo (bloquea el hilo del pedido si no hay disponibles)
        Semaphore sem = semaphores.get(resource);
        sem.acquire(count);

        // Al obtener el recurso, convertir la arista de solicitud a arista de asignación (Resource -> Order)
        ragLock.lock();
        try {
            orderWaitingFor.remove(order);
            order.setWaitingForResource(null);
            order.addAllocatedResource(resource);
            resourceHolders.get(resource).add(order);
        } finally {
            ragLock.unlock();
        }
    }

    /**
     * Libera un recurso previamente adquirido.
     */
    public void releaseResource(Order order, ResourceType resource, int count) {
        ragLock.lock();
        try {
            resourceHolders.get(resource).remove(order);
            order.removeAllocatedResource(resource);
        } finally {
            ragLock.unlock();
        }

        Semaphore sem = semaphores.get(resource);
        sem.release(count);
    }

    /**
     * Libera deliberadamente todos los recursos retenidos por un pedido.
     * Esencial para la resolución manual de Deadlocks (preempting / expulsión de recursos).
     */
    public void releaseAllResources(Order order) {
        ragLock.lock();
        try {
            orderWaitingFor.remove(order);
            order.setWaitingForResource(null);

            Set<ResourceType> held = new HashSet<>(order.getAllocatedResources());
            for (ResourceType res : held) {
                resourceHolders.get(res).remove(order);
                order.removeAllocatedResource(res);
                semaphores.get(res).release(1);
            }
        } finally {
            ragLock.unlock();
        }
    }

    public int getAvailableUnits(ResourceType resource) {
        return semaphores.get(resource).availablePermits();
    }

    public int getOccupiedUnits(ResourceType resource) {
        return resource.getTotalCapacity() - getAvailableUnits(resource);
    }

    public double getUtilizationPercentage(ResourceType resource) {
        return ((double) getOccupiedUnits(resource) / resource.getTotalCapacity()) * 100.0;
    }

    public Set<Order> getHolders(ResourceType resource) {
        return Collections.unmodifiableSet(resourceHolders.get(resource));
    }

    public ResourceType getWaitingResource(Order order) {
        return orderWaitingFor.get(order);
    }

    public Map<Order, ResourceType> getOrderWaitingForMap() {
        ragLock.lock();
        try {
            return new HashMap<>(orderWaitingFor);
        } finally {
            ragLock.unlock();
        }
    }

    public Map<ResourceType, Set<Order>> getResourceHoldersMap() {
        ragLock.lock();
        try {
            Map<ResourceType, Set<Order>> copy = new EnumMap<>(ResourceType.class);
            for (Map.Entry<ResourceType, Set<Order>> e : resourceHolders.entrySet()) {
                copy.put(e.getKey(), new HashSet<>(e.getValue()));
            }
            return copy;
        } finally {
            ragLock.unlock();
        }
    }

    public ReentrantLock getRagLock() {
        return ragLock;
    }
}
