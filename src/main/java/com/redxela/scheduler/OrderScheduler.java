package com.redxela.scheduler;

import com.redxela.concurrency.DeadlockDetector;
import com.redxela.concurrency.ResourceManager;
import com.redxela.model.*;
import com.redxela.model.enums.*;
import com.redxela.storage.WarehouseStorageManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Planificador central de pedidos del centro de distribucion.
 * Gestiona la cola de espera segun la politica de planificacion activa,
 * despacha pedidos concurrentes en hilos independientes y coordina la
 * asignacion
 * de recursos fisicos paso a paso.
 */
public class OrderScheduler {

    // Administrador de recursos fisicos y semaforos.
    private final ResourceManager resourceManager;

    // Gestor de almacenamiento fisico no contiguo e inventario.
    private final WarehouseStorageManager storageManager;

    // Detector de ciclos en el grafo RAG para interbloqueos.
    private final DeadlockDetector deadlockDetector;

    // Estrategia de planificacion activa actualmente en la simulacion.
    private volatile SchedulingPolicy currentPolicy;

    // Cola de espera protegida de pedidos listos para ser atendidos.
    private final List<Order> waitingQueue = new ArrayList<>();

    // Lista thread-safe de pedidos que actualmente estan corriendo en hilos de
    // trabajo.
    private final List<Order> processingOrders = new CopyOnWriteArrayList<>();

    // Historial de pedidos que completaron satisfactoriamente todas sus fases de
    // empaque y despacho.
    private final List<Order> completedOrders = new CopyOnWriteArrayList<>();

    // Cerrojo de exclusion mutua para la manipulacion atomica de la cola de espera.
    private final ReentrantLock schedulerLock = new ReentrantLock(true);

    // Grupo de hilos de trabajo bajo demanda para la ejecucion concurrente de
    // pedidos.
    private final ExecutorService workerThreadPool;

    // Planificador de tareas periodicas para el despacho continuo y monitoreo de
    // deadlocks.
    private final ScheduledExecutorService monitoringExecutor;

    // Estado de ejecucion del planificador.
    private volatile boolean isRunning = false;

    // Multiplicador de velocidad de la simulacion (1.0 = normal, 2.0 = el doble de
    // rapido).
    private volatile double simulationSpeedMultiplier = 1.0;

    /**
     * Construye el planificador inicializando los grupos de hilos y la politica por
     * defecto (Prioridad Estricta).
     *
     * @param resourceManager  Gestor de semaforos compartidos.
     * @param storageManager   Gestor de inventario y slots de almacen.
     * @param deadlockDetector Detector de interbloqueos RAG.
     */
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

    /**
     * Inicia los bucles periodicos de despacho de pedidos y deteccion de deadlocks
     * en segundo plano.
     */
    public void start() {
        if (isRunning)
            return;
        isRunning = true;

        // Despacho continuo de la cola de espera (cada 200 ms)
        monitoringExecutor.scheduleWithFixedDelay(this::dispatchPendingOrders, 200, 200, TimeUnit.MILLISECONDS);

        // Inspeccion continua del grafo RAG en busqueda de deadlocks (cada 300 ms)
        monitoringExecutor.scheduleWithFixedDelay(deadlockDetector::checkForDeadlock, 300, 300, TimeUnit.MILLISECONDS);
    }

    /**
     * Detiene de forma ordenada todos los hilos de trabajo y monitores.
     */
    public void stop() {
        isRunning = false;
        workerThreadPool.shutdownNow();
        monitoringExecutor.shutdownNow();
    }

    /**
     * Encola un nuevo pedido en la lista de espera, reordenandola segun la politica
     * activa.
     *
     * @param order Pedido a ingresar al sistema.
     */
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

    /**
     * Cambia la politica de planificacion en tiempo real y reordena inmediatamente
     * la cola de espera.
     *
     * @param newPolicy Nueva politica a aplicar (AgingPriorityPolicy o SjfPolicy).
     */
    public void setPolicy(SchedulingPolicy newPolicy) {
        schedulerLock.lock();
        try {
            this.currentPolicy = newPolicy;
            sortWaitingQueue();
        } finally {
            schedulerLock.unlock();
        }
    }

    /**
     * Reordena la cola de espera utilizando el comparador de la politica activa.
     */
    private void sortWaitingQueue() {
        waitingQueue.sort(currentPolicy.getComparator());
    }

    /**
     * Bucle periodico de despacho:
     * Inspecciona la cabeza de la cola de espera, verifica si hay stock fisico
     * suficiente en almacen
     * y, de ser asi, despacha el pedido a un hilo de trabajo concurrente.
     */
    private void dispatchPendingOrders() {
        if (!isRunning)
            return;

        List<Order> toDispatch = new ArrayList<>();
        schedulerLock.lock();
        try {
            sortWaitingQueue(); // Reordenar para actualizar puntajes dinamicos de envejecimiento (Aging)

            Iterator<Order> it = waitingQueue.iterator();
            while (it.hasNext()) {
                Order order = it.next();

                // Verificar existencias fisicas en el almacen
                if (!storageManager.hasStockForOrder(order.getItems())) {
                    order.setStatus(OrderStatus.EN_ESPERA_INVENTARIO);
                    order.setCurrentPhaseDescription("Esperando recepción de stock en almacén");
                    continue; // Pasa al siguiente pedido disponible
                }

                // Si tiene stock, se despacha a ejecucion concurrente
                it.remove();
                toDispatch.add(order);
            }
        } finally {
            schedulerLock.unlock();
        }

        // Lanzar cada pedido en un hilo independiente del ThreadPool
        for (Order order : toDispatch) {
            order.setScheduledAt(System.currentTimeMillis());
            order.setProcessingStartedAt(System.currentTimeMillis());
            order.setStatus(OrderStatus.EN_PROCESO);
            processingOrders.add(order);
            workerThreadPool.submit(() -> processOrderLifecycle(order));
        }
    }

    /**
     * Ciclo de vida concurrente de un pedido en ejecucion (codigo del hilo
     * trabajador).
     *
     * @param order Pedido a procesar.
     */
    private void processOrderLifecycle(Order order) {
        try {
            // Descontar inventario fisico del almacen
            for (Map.Entry<Product, Integer> entry : order.getItems().entrySet()) {
                storageManager.consumeStock(entry.getKey(), entry.getValue());
            }

            // Si es un test de deadlock deliberado, ejecutar la secuencia cruzada
            if (order.isForceDeadlockTest()) {
                executeDeadlockScenario(order);
                return;
            }

            // Ejecucion normal por fases segun el tipo de mercancia
            List<ResourceRequirement> steps = order.getMerchandiseType().getSteps();
            int totalSteps = steps.size();

            for (int i = 0; i < totalSteps; i++) {
                ResourceRequirement req = steps.get(i);
                order.setCurrentPhaseDescription(
                        "Solicitando " + req.resourceType().getDisplayName() + " (" + req.phaseDescription() + ")");

                // Adquisicion de recurso (bloquea si no hay disponible)
                resourceManager.acquireResource(order, req.resourceType(), req.count());

                order.setCurrentPhaseDescription("Ejecutando: " + req.phaseDescription());

                // Simular tiempo de trabajo en la estacion (proporcional a cantidad de
                // articulos)
                long workDurationMs = (long) ((req.count() * 1200L + order.getTotalUnits() * 400L)
                        / simulationSpeedMultiplier);
                simulateWork(order, workDurationMs, (i * 100) / totalSteps, ((i + 1) * 100) / totalSteps);

                // Liberar el recurso al concluir la fase
                resourceManager.releaseResource(order, req.resourceType(), req.count());
            }

            // Pedido completado con exito
            order.setProgressPercent(100);
            order.setCompletedAt(System.currentTimeMillis());
            order.setStatus(OrderStatus.COMPLETADO);
            order.setCurrentPhaseDescription("Despachado y entregado exitosamente");

            processingOrders.remove(order);
            completedOrders.add(order);

        } catch (InterruptedException e) {
            // Hilo interrumpido o desalojado por resolucion de deadlock
            order.setCurrentPhaseDescription("Interrumpido / Desalojado de recursos");
        } finally {
            resourceManager.releaseAllResources(order);
        }
    }

    /**
     * Ejecuta deliberadamente la secuencia de contencion cruzada de Deadlock:
     * El pedido Fragil toma Calidad y pide Montacargas;
     * El pedido Pesado toma Montacargas y pide Calidad.
     *
     * @param order Pedido involucrado en el escenario de prueba.
     * @throws InterruptedException Si el hilo es interrumpido durante la espera.
     */
    private void executeDeadlockScenario(Order order) throws InterruptedException {
        if (order.getMerchandiseType() == MerchandiseType.FRAGIL) {
            order.setCurrentPhaseDescription("DEADLOCK TEST: Reteniendo Control de Calidad...");
            resourceManager.acquireResource(order, ResourceType.QUALITY_STATION, 1);
            order.setProgressPercent(30);

            // Pausa breve para garantizar que el pedido Pesado retenga el Montacargas
            Thread.sleep(1500);

            order.setCurrentPhaseDescription("DEADLOCK TEST: Solicitando Montacargas (¡Esperando!)...");
            // Intentar adquirir Montacargas (quedara bloqueado en Deadlock)
            resourceManager.acquireResource(order, ResourceType.FORKLIFT, 1);

            // Si sobrevive a la resolucion de deadlock:
            finishSuccessfulDeadlockProcessing(order);

        } else if (order.getMerchandiseType() == MerchandiseType.PESADA) {
            order.setCurrentPhaseDescription("DEADLOCK TEST: Reteniendo Montacargas...");
            resourceManager.acquireResource(order, ResourceType.FORKLIFT, 1);
            order.setProgressPercent(30);

            // Pausa para sincronizar con el pedido Fragil
            Thread.sleep(1500);

            order.setCurrentPhaseDescription("DEADLOCK TEST: Solicitando Control de Calidad (¡Esperando!)...");
            // Intentar adquirir Control de Calidad (quedara bloqueado en Deadlock)
            resourceManager.acquireResource(order, ResourceType.QUALITY_STATION, 1);

            // Si sobrevive a la resolucion de deadlock:
            finishSuccessfulDeadlockProcessing(order);
        }
    }

    /**
     * Concluye el procesamiento normal del pedido que resulto favorecido tras la
     * resolucion del Deadlock.
     *
     * @param order Pedido ganador.
     * @throws InterruptedException Si es interrumpido.
     */
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

    /**
     * Simula el paso del tiempo de una operacion fisica actualizando
     * progresivamente el porcentaje de avance.
     *
     * @param order           Pedido a actualizar.
     * @param totalDurationMs Duracion total en milisegundos.
     * @param startPercent    Porcentaje inicial.
     * @param endPercent      Porcentaje final.
     * @throws InterruptedException Si el hilo es interrumpido.
     */
    private void simulateWork(Order order, long totalDurationMs, int startPercent, int endPercent)
            throws InterruptedException {
        int steps = 10;
        long stepDuration = Math.max(10, totalDurationMs / steps);
        for (int s = 1; s <= steps; s++) {
            Thread.sleep(stepDuration);
            int current = startPercent + (int) (((double) s / steps) * (endPercent - startPercent));
            order.setProgressPercent(current);
        }
    }

    /**
     * Reincorpora un pedido a la cola de espera tras haber sido desalojado en la
     * resolucion de un Deadlock.
     *
     * @param order Pedido a reencolar.
     */
    public void reenqueuePreemptedOrder(Order order) {
        processingOrders.remove(order);
        addOrder(order);
    }

    /**
     * Retorna la politica de atencion activa.
     *
     * @return {@link SchedulingPolicy} actual.
     */
    public SchedulingPolicy getCurrentPolicy() {
        return currentPolicy;
    }

    /**
     * Retorna una copia de la cola de espera.
     *
     * @return Lista de pedidos en espera.
     */
    public List<Order> getWaitingQueue() {
        schedulerLock.lock();
        try {
            return new ArrayList<>(waitingQueue);
        } finally {
            schedulerLock.unlock();
        }
    }

    /**
     * Retorna la lista de pedidos en procesamiento activo.
     *
     * @return Lista de pedidos activos.
     */
    public List<Order> getProcessingOrders() {
        return new ArrayList<>(processingOrders);
    }

    /**
     * Retorna la lista de pedidos completados.
     *
     * @return Lista de pedidos despachados.
     */
    public List<Order> getCompletedOrders() {
        return new ArrayList<>(completedOrders);
    }

    /**
     * Retorna el factor de velocidad de simulacion.
     *
     * @return Multiplicador de velocidad.
     */
    public double getSimulationSpeedMultiplier() {
        return simulationSpeedMultiplier;
    }

    /**
     * Ajusta el factor de velocidad de la simulacion.
     *
     * @param simulationSpeedMultiplier Nuevo factor de velocidad.
     */
    public void setSimulationSpeedMultiplier(double simulationSpeedMultiplier) {
        this.simulationSpeedMultiplier = Math.max(0.2, simulationSpeedMultiplier);
    }
}
