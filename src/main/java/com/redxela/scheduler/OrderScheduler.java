package com.redxela.scheduler;

import com.redxela.concurrency.DeadlockDetector;
import com.redxela.concurrency.ResourceManager;
import com.redxela.model.*;
import com.redxela.storage.WarehouseStorageManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Planificador central de pedidos del Centro de Distribución REDXela.
 * Administra la cola de espera, ejecuta la política de despacho seleccionada
 * y coordina la ejecución concurrente mediante hilos de trabajo.
 */
public class OrderScheduler {
    private final ResourceManager resourceManager;
    private final WarehouseStorageManager storageManager;
    private final DeadlockDetector deadlockDetector;

    // Políticas disponibles
    private volatile SchedulingPolicy currentPolicy;

    // Cola de espera de pedidos (protegida por schedulerLock)
    private final List<Order> waitingQueue = new ArrayList<>();
    // Pedidos en procesamiento activo
    private final List<Order> processingOrders = new CopyOnWriteArrayList<>();
    // Pedidos completados
    private final List<Order> completedOrders = new CopyOnWriteArrayList<>();

    private final ReentrantLock schedulerLock = new ReentrantLock(true);
    private final ExecutorService workerThreadPool;
    private final ScheduledExecutorService monitoringExecutor;

    private volatile boolean isRunning = false;
    private volatile double simulationSpeedMultiplier = 1.0; // 1.0 = normal, 2.0 = 2x rápido

    public OrderScheduler(ResourceManager resourceManager,
                          WarehouseStorageManager storageManager,
                          DeadlockDetector deadlockDetector) {
        this.resourceManager = resourceManager;
        this.storageManager = storageManager;
        this.deadlockDetector = deadlockDetector;
        this.currentPolicy = new StrictPriorityPolicy();

        this.workerThreadPool = Executors.newCachedThreadPool();
        this.monitoringExecutor = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        // Tarea periódica de despacho de pedidos de la cola
        monitoringExecutor.scheduleWithFixedDelay(this::dispatchPendingOrders, 200, 200, TimeUnit.MILLISECONDS);

        // Tarea periódica de inspección de Deadlocks en el grafo RAG
        monitoringExecutor.scheduleWithFixedDelay(deadlockDetector::checkForDeadlock, 300, 300, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        isRunning = false;
        workerThreadPool.shutdownNow();
        monitoringExecutor.shutdownNow();
    }

    public void addOrder(Order order) {
        schedulerLock.lock();
        try {
            order.setStatus(OrderStatus.EN_ESPERA_RECURSOS);
            order.setCurrentPhaseDescription("En cola de espera (" + currentPolicy.getName() + ")");
            waitingQueue.add(order);
            sortWaitingQueue();
        } finally {
            schedulerLock.unlock();
        }
    }

    public void setPolicy(SchedulingPolicy newPolicy) {
        schedulerLock.lock();
        try {
            this.currentPolicy = newPolicy;
            sortWaitingQueue();
        } finally {
            schedulerLock.unlock();
        }
    }

    private void sortWaitingQueue() {
        waitingQueue.sort(currentPolicy.getComparator());
    }

    /**
     * Bucle de despacho: Revisa pedidos en espera e inicia su procesamiento si hay stock e inventario.
     */
    private void dispatchPendingOrders() {
        if (!isRunning) return;

        List<Order> toDispatch = new ArrayList<>();
        schedulerLock.lock();
        try {
            sortWaitingQueue(); // Reordenar si aplica política de envejecimiento (Aging)

            Iterator<Order> it = waitingQueue.iterator();
            while (it.hasNext()) {
                Order order = it.next();

                // Verificar existencias físicas en el almacén
                if (!storageManager.hasStockForOrder(order.getItems())) {
                    order.setStatus(OrderStatus.EN_ESPERA_INVENTARIO);
                    order.setCurrentPhaseDescription("Esperando recepción de stock en almacén");
                    continue; // Pasa al siguiente pedido disponible
                }

                // Si tiene stock, se despacha a ejecución concurrente
                it.remove();
                toDispatch.add(order);
            }
        } finally {
            schedulerLock.unlock();
        }

        for (Order order : toDispatch) {
            order.setScheduledAt(System.currentTimeMillis());
            order.setProcessingStartedAt(System.currentTimeMillis());
            order.setStatus(OrderStatus.EN_PROCESO);
            processingOrders.add(order);
            workerThreadPool.submit(() -> processOrderLifecycle(order));
        }
    }

    /**
     * Ciclo de vida concurrente de un pedido en ejecución (Hilo de trabajo).
     */
    private void processOrderLifecycle(Order order) {
        try {
            // Descontar inventario físico del almacén
            for (Map.Entry<Product, Integer> entry : order.getItems().entrySet()) {
                storageManager.consumeStock(entry.getKey(), entry.getValue());
            }

            // Si es un test de deadlock deliberado, ejecutar la secuencia cruzada
            if (order.isForceDeadlockTest()) {
                executeDeadlockScenario(order);
                return;
            }

            // Ejecución normal por fases según el tipo de mercancía
            List<MerchandiseType.ResourceRequirement> steps = order.getMerchandiseType().getSteps();
            int totalSteps = steps.size();

            for (int i = 0; i < totalSteps; i++) {
                MerchandiseType.ResourceRequirement req = steps.get(i);
                order.setCurrentPhaseDescription("Solicitando " + req.resourceType().getDisplayName() + " (" + req.phaseDescription() + ")");

                // Adquisición de recurso (bloquea si no hay disponible)
                resourceManager.acquireResource(order, req.resourceType(), req.count());

                order.setCurrentPhaseDescription("Ejecutando: " + req.phaseDescription());

                // Simular tiempo de trabajo en la estación (proporcional a cantidad de artículos)
                long workDurationMs = (long) ((req.count() * 1200L + order.getTotalUnits() * 400L) / simulationSpeedMultiplier);
                simulateWork(order, workDurationMs, (i * 100) / totalSteps, ((i + 1) * 100) / totalSteps);

                // Liberar el recurso al concluir la fase
                resourceManager.releaseResource(order, req.resourceType(), req.count());
            }

            // Pedido completado con éxito
            order.setProgressPercent(100);
            order.setCompletedAt(System.currentTimeMillis());
            order.setStatus(OrderStatus.COMPLETADO);
            order.setCurrentPhaseDescription("Despachado y entregado exitosamente");

            processingOrders.remove(order);
            completedOrders.add(order);

        } catch (InterruptedException e) {
            // Hilo interrumpido o desalojado por resolución de deadlock
            order.setCurrentPhaseDescription("Interrumpido / Desalojado de recursos");
        } finally {
            resourceManager.releaseAllResources(order);
        }
    }

    /**
     * Escenario deliberado de Deadlock:
     * El Pedido Frágil adquiere Calidad y luego pide Montacargas.
     * El Pedido Pesado adquiere Montacargas y luego pide Calidad.
     */
    private void executeDeadlockScenario(Order order) throws InterruptedException {
        if (order.getMerchandiseType() == MerchandiseType.FRAGIL) {
            order.setCurrentPhaseDescription("DEADLOCK TEST: Reteniendo Control de Calidad...");
            resourceManager.acquireResource(order, ResourceType.QUALITY_STATION, 1);
            order.setProgressPercent(30);

            // Pausa para dar tiempo a que el pedido Pesado retenga el Montacargas
            Thread.sleep(1500);

            order.setCurrentPhaseDescription("DEADLOCK TEST: Solicitando Montacargas (¡Esperando!)...");
            // Intentar adquirir Montacargas (quedará bloqueado en Deadlock)
            resourceManager.acquireResource(order, ResourceType.FORKLIFT, 1);

            // Si sobrevive a la resolución de deadlock:
            finishSuccessfulDeadlockProcessing(order);

        } else if (order.getMerchandiseType() == MerchandiseType.PESADA) {
            order.setCurrentPhaseDescription("DEADLOCK TEST: Reteniendo Montacargas...");
            resourceManager.acquireResource(order, ResourceType.FORKLIFT, 1);
            order.setProgressPercent(30);

            // Pausa para sincronizar con el pedido Frágil
            Thread.sleep(1500);

            order.setCurrentPhaseDescription("DEADLOCK TEST: Solicitando Control de Calidad (¡Esperando!)...");
            // Intentar adquirir Control de Calidad (quedará bloqueado en Deadlock)
            resourceManager.acquireResource(order, ResourceType.QUALITY_STATION, 1);

            // Si sobrevive a la resolución de deadlock:
            finishSuccessfulDeadlockProcessing(order);
        }
    }

    private void finishSuccessfulDeadlockProcessing(Order order) throws InterruptedException {
        order.setStatus(OrderStatus.EN_PROCESO);
        order.setCurrentPhaseDescription("Reanudado tras resolución de Deadlock: Procesando...");
        simulateWork(order, (long) (3000 / simulationSpeedMultiplier), 40, 100);

        resourceManager.releaseAllResources(order);
        order.setProgressPercent(100);
        order.setCompletedAt(System.currentTimeMillis());
        order.setStatus(OrderStatus.COMPLETADO);
        order.setCurrentPhaseDescription("Completado tras resolver Deadlock exitosamente");

        processingOrders.remove(order);
        completedOrders.add(order);
    }

    private void simulateWork(Order order, long totalDurationMs, int startPercent, int endPercent) throws InterruptedException {
        int steps = 10;
        long stepDuration = Math.max(10, totalDurationMs / steps);
        for (int s = 1; s <= steps; s++) {
            Thread.sleep(stepDuration);
            int current = startPercent + (int) (((double) s / steps) * (endPercent - startPercent));
            order.setProgressPercent(current);
        }
    }

    public void reenqueuePreemptedOrder(Order order) {
        processingOrders.remove(order);
        addOrder(order);
    }

    public SchedulingPolicy getCurrentPolicy() {
        return currentPolicy;
    }

    public List<Order> getWaitingQueue() {
        schedulerLock.lock();
        try {
            return new ArrayList<>(waitingQueue);
        } finally {
            schedulerLock.unlock();
        }
    }

    public List<Order> getProcessingOrders() {
        return new ArrayList<>(processingOrders);
    }

    public List<Order> getCompletedOrders() {
        return new ArrayList<>(completedOrders);
    }

    public double getSimulationSpeedMultiplier() {
        return simulationSpeedMultiplier;
    }

    public void setSimulationSpeedMultiplier(double simulationSpeedMultiplier) {
        this.simulationSpeedMultiplier = Math.max(0.2, simulationSpeedMultiplier);
    }
}
