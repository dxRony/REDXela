package com.redxela.concurrency;

import com.redxela.model.Order;
import com.redxela.model.enums.ResourceType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Administra los recursos fisicos compartidos del centro de distribucion utilizando
 * semaforos contadores justos (FIFO) y mantiene el Grafo de Asignacion de Recursos.
 */
public class ResourceManager {

    // Tabla de semaforos contadores para cada tipo de recurso fisico disponible en el centro. Cada
    // semaforo se inicializa con la capacidad total definida en ResourceType.getTotalCapacity().
    private final Map<ResourceType, Semaphore> semaphores = new EnumMap<>(ResourceType.class);

    // Mapeo del Grafo de Asignacion de Recursos (RAG): Representa las Aristas de Asignacion (Recurso ->
    // Procesos). Indica el conjunto de pedidos (hilos) que actualmente retienen instancias de cada
    // recurso.
    private final Map<ResourceType, Set<Order>> resourceHolders = new EnumMap<>(ResourceType.class);

    // Mapeo del Grafo de Asignacion de Recursos (RAG): Representa las Aristas de Solicitud / Espera
    // (Proceso -> Recurso). Indica que recurso especifico esta esperando adquirir cada pedido que se
    // encuentra bloqueado.
    private final Map<Order, ResourceType> orderWaitingFor = new ConcurrentHashMap<>();

    // Registro de cuantas unidades retiene cada pedido de cada recurso para liberacion exacta
    private final Map<Order, Map<ResourceType, Integer>> orderAllocatedCounts = new ConcurrentHashMap<>();

    // Cerrojo de exclusion mutua para garantizar operaciones atomicas e indivisibles sobre la topologia
    // del Grafo de Asignacion de Recursos (RAG).
    private final ReentrantLock ragLock = new ReentrantLock();

    /**
     * Construye e inicializa los semaforos contadores de recursos con equidad justa (FIFO).
     */
    public ResourceManager() {
        for (ResourceType type : ResourceType.values()) {
            // true = Fairness activado (orden FIFO para despertar hilos bloqueados)
            semaphores.put(type, new Semaphore(type.getTotalCapacity(), true));
            resourceHolders.put(type, ConcurrentHashMap.newKeySet());
        }
    }

    /**
     * Registra explicitamente en el grafo RAG que un pedido esta en espera de un recurso.
     * Util para pruebas y control fino de sincronizacion.
     *
     * @param order Pedido que solicita el recurso.
     * @param resource Recurso fisico disputado.
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
     * Intenta adquirir permisos de un recurso fisico compartido.
     * 
     * Flujo de Concurrencia de SO:
     *   - Registra la arista de solicitud Order -> Resource en el Grafo RAG.
     *   - Invoca Semaphore.acquire(int). Si no hay permisos suficientes disponibles,
     *     el hilo de trabajo del pedido se suspende (estado BLOCKED / WAITING).
     *   - Al despertar con los permisos en posesion, se transforma la arista de solicitud
     *     en una arista de asignacion Resource -> Order.
     * 
     * @param order Pedido en ejecucion concurrente que requiere el recurso.
     * @param resource Tipo de recurso fisico solicitado.
     * @param count Cantidad de instancias requeridas simultaneamente.
     * @throws InterruptedException Si el hilo es interrumpido mientras espera los permisos.
     */
    public void acquireResource(Order order, ResourceType resource, int count) throws InterruptedException {
        // registrando arista de solicitud en el RAG (Order -> Resource)
        ragLock.lock();
        try {
            order.setWaitingForResource(resource);
            orderWaitingFor.put(order, resource);
        } finally {
            ragLock.unlock();
        }

        // adquisicion bloqueante en el semaforo contador (suspension de hilo en espera)
        Semaphore sem = semaphores.get(resource);
        try {
            sem.acquire(count);
        } catch (InterruptedException e) {
            // si el hilo fue interrumpido mientras esperaba en el semaforo, limpiar la solicitud
            ragLock.lock();
            try {
                orderWaitingFor.remove(order);
                order.setWaitingForResource(null);
            } finally {
                ragLock.unlock();
            }
            throw e;
        }

        // cuando despierta, convierte la solicitud en arista de asignacion activa (Resource -> Order)
        ragLock.lock();
        try {
            orderWaitingFor.remove(order);
            order.setWaitingForResource(null);
            order.addAllocatedResource(resource);
            resourceHolders.get(resource).add(order);
            orderAllocatedCounts.computeIfAbsent(order, k -> new ConcurrentHashMap<>())
                                .merge(resource, count, Integer::sum);
        } finally {
            ragLock.unlock();
        }
    }

    /**
     * Libera las unidades de un recurso fisico previamente retenido por un pedido.
     * 
     * @param order Pedido que concluyo el uso del recurso.
     * @param resource Recurso fisico liberado.
     * @param count Cantidad de permisos a reincorporar al semaforo.
     */
    public void releaseResource(Order order, ResourceType resource, int count) {
        ragLock.lock();
        try {
            Map<ResourceType, Integer> counts = orderAllocatedCounts.get(order);
            if (counts != null) {
                Integer current = counts.get(resource);
                if (current != null) {
                    if (current <= count) {
                        counts.remove(resource);
                        resourceHolders.get(resource).remove(order);
                        order.removeAllocatedResource(resource);
                    } else {
                        counts.put(resource, current - count);
                    }
                }
            } else {
                resourceHolders.get(resource).remove(order);
                order.removeAllocatedResource(resource);
            }
        } finally {
            ragLock.unlock();
        }

        // devolver permisos al semaforo para despertar hilos en espera
        Semaphore sem = semaphores.get(resource);
        sem.release(count);
    }

    /**
     * Libera de forma atomica y forzosa todos los recursos retenidos por un pedido.
     * 
     * Mecanismo de Desalojo (Preemption):
     * Esencial para la resolucion manual de Deadlocks. Permite romper la condicion
     * de Hold and Wait despojando al proceso de sus recursos para asignarselos al ganador.
     * 
     * @param order Pedido seleccionado como victima del desalojo.
     */
    public void releaseAllResources(Order order) {
        ragLock.lock();
        try {
            orderWaitingFor.remove(order);
            order.setWaitingForResource(null);

            Map<ResourceType, Integer> counts = orderAllocatedCounts.remove(order);
            if (counts != null && !counts.isEmpty()) {
                for (Map.Entry<ResourceType, Integer> entry : counts.entrySet()) {
                    ResourceType res = entry.getKey();
                    int heldCount = entry.getValue();
                    resourceHolders.get(res).remove(order);
                    order.removeAllocatedResource(res);
                    semaphores.get(res).release(heldCount);
                }
            } else {
                // fallback defensivo si no habia conteo explicito
                Set<ResourceType> held = new HashSet<>(order.getAllocatedResources());
                for (ResourceType res : held) {
                    resourceHolders.get(res).remove(order);
                    order.removeAllocatedResource(res);
                    semaphores.get(res).release(1);
                }
            }
        } finally {
            ragLock.unlock();
        }
    }

    /**
     * Consulta el numero de unidades disponibles actualmente en el semaforo del recurso.
     *
     * @param resource Recurso a consultar.
     * @return Cantidad de permisos libres.
     */
    public int getAvailableUnits(ResourceType resource) {
        return semaphores.get(resource).availablePermits();
    }

    /**
     * Consulta el numero de unidades en uso en este instante.
     *
     * @param resource Recurso a consultar.
     * @return Capacidad total menos permisos disponibles.
     */
    public int getOccupiedUnits(ResourceType resource) {
        return resource.getTotalCapacity() - getAvailableUnits(resource);
    }

    /**
     * Calcula el porcentaje de aprovechamiento o utilizacion del recurso.
     *
     * @param resource Recurso a evaluar.
     * @return Porcentaje de 0.0% a 100.0%.
     */
    public double getUtilizationPercentage(ResourceType resource) {
        return ((double) getOccupiedUnits(resource) / resource.getTotalCapacity()) * 100.0;
    }

    /**
     * Retorna una vista de solo lectura de los pedidos que actualmente ocupan el recurso.
     *
     * @param resource Recurso a consultar.
     * @return Conjunto inmutable de pedidos duenos del recurso.
     */
    public Set<Order> getHolders(ResourceType resource) {
        return Collections.unmodifiableSet(resourceHolders.get(resource));
    }

    /**
     * Obtiene el recurso por el cual un pedido especifico se encuentra en espera.
     *
     * @param order Pedido a inspeccionar.
     * @return ResourceType esperado, o null si no esta bloqueado.
     */
    public ResourceType getWaitingResource(Order order) {
        return orderWaitingFor.get(order);
    }

    /**
     * Retorna una instantanea defensiva del mapa de solicitudes en espera del grafo RAG.
     *
     * @return Copia segura del mapa Pedido -> RecursoEsperado.
     */
    public Map<Order, ResourceType> getOrderWaitingForMap() {
        ragLock.lock();
        try {
            return new HashMap<>(orderWaitingFor);
        } finally {
            ragLock.unlock();
        }
    }

    /**
     * Retorna una instantanea defensiva del mapa de tenedores de recursos del grafo RAG.
     *
     * @return Copia segura del mapa Recurso -> ConjuntoDePedidos
     */
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

    /**
     * Proporciona acceso al cerrojo de sincronizacion del RAG para operaciones coordinadas.
     *
     * @return Instancia del ReentrantLock del RAG.
     */
    public ReentrantLock getRagLock() {
        return ragLock;
    }
}
