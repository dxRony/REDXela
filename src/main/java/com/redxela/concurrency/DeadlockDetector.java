package com.redxela.concurrency;

import com.redxela.model.Order;
import com.redxela.model.enums.OrderStatus;
import com.redxela.model.enums.ResourceType;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Detector y gestor de interbloqueos (Deadlocks) basado en el Grafo de
 * Asignacion de Recursos
 * Identifica ciclos de espera circular mutua y permite la resolucion manual
 * mediante desalojo.
 */
public class DeadlockDetector {

    // Referencia al administrador de recursos que mantiene el estado del RAG.
    private final ResourceManager resourceManager;

    // Lista de observadores registrados para recibir notificaciones de eventos de
    // Deadlock. Utiliza
    // CopyOnWriteArrayList para garantizar iteraciones thread-safe.
    private final List<DeadlockListener> listeners = new CopyOnWriteArrayList<>();

    // Informacion del interbloqueo actualmente activo que requiere intervencion
    // manual. Es
    // volatile para garantizar visibilidad inmediata entre hilos.
    private volatile DeadlockInfo currentDeadlock = null;

    /**
     * Interfaz de escucha para eventos de ciclo de vida de un interbloqueo.
     */
    public interface DeadlockListener {
        /**
         * Invocado cuando el detector confirma la existencia de un ciclo en el RAG.
         *
         * @param info Diagnostico detallado del interbloqueo.
         */
        void onDeadlockDetected(DeadlockInfo info);

        /**
         * Invocado cuando el interbloqueo ha sido resuelto manualmente.
         *
         * @param info Informacion del interbloqueo resuelto.
         */
        void onDeadlockResolved(DeadlockInfo info);
    }

    /**
     * Crea una instancia del detector vinculada al administrador de recursos.
     *
     * @param resourceManager Gestor central de semaforos y asignaciones.
     */
    public DeadlockDetector(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    /**
     * Registra un nuevo observador para eventos de Deadlock.
     *
     * @param listener Observador a registrar.
     */
    public void addListener(DeadlockListener listener) {
        listeners.add(listener);
    }

    /**
     * Remueve un observador previamente registrado.
     *
     * @param listener Observador a eliminar.
     */
    public void removeListener(DeadlockListener listener) {
        listeners.remove(listener);
    }

    /**
     * Retorna la informacion del interbloqueo activo actual, o null si el sistema
     * esta libre de bloqueos.
     *
     * @return Informacion del deadlock activo.
     */
    public DeadlockInfo getCurrentDeadlock() {
        return currentDeadlock;
    }

    /**
     * Inspecciona la topologia actual del Grafo de Asignacion de Recursos (RAG)
     * en busca de un ciclo cerrado de espera mutua (Wait-For Cycle).
     * 
     * Algoritmo de Deteccion:
     * Para cada pedido {@code A} que espera un recurso {@code R1}, busca los
     * pedidos {@code B}
     * que actualmente retienen {@code R1}. Luego verifica si {@code B} esta a su
     * vez esperando
     * algun recurso {@code R2} que este en posesion de {@code A}.
     * Si dicha condicion se cumple:
     * $$A \xrightarrow{\text{espera}} R_1 \xleftarrow{\text{retenido}} B
     * \xrightarrow{\text{espera}} R_2 \xleftarrow{\text{retenido}} A$$
     * Se confirma un ciclo cerrado de interbloqueo mutuo.
     * 
     * @return {@link DeadlockInfo} con el diagnostico si se detecta un ciclo, o
     *         {@code null} si no hay deadlock.
     */
    public DeadlockInfo checkForDeadlock() {
        if (currentDeadlock != null) {
            return currentDeadlock; // Ya existe un deadlock activo esperando resolucion manual
        }

        Map<Order, ResourceType> waitingMap = resourceManager.getOrderWaitingForMap();
        Map<ResourceType, Set<Order>> holdersMap = resourceManager.getResourceHoldersMap();

        // Buscar ciclo de espera mutua entre pares de procesos en el grafo
        for (Map.Entry<Order, ResourceType> entryA : waitingMap.entrySet()) {
            Order orderA = entryA.getKey();
            ResourceType resWaitedByA = entryA.getValue();

            // Conjunto de procesos que retienen el recurso esperado por A
            Set<Order> holdersOfWaitedRes = holdersMap.getOrDefault(resWaitedByA, Collections.emptySet());

            for (Order orderB : holdersOfWaitedRes) {
                if (orderB.equals(orderA))
                    continue;

                // Verificar si B esta esperando algun recurso que retiene A
                ResourceType resWaitedByB = waitingMap.get(orderB);
                if (resWaitedByB != null && orderA.getAllocatedResources().contains(resWaitedByB)) {
                    // ¡CICLO CONFIRMADO! Se satisfacen las condiciones de Coffman
                    orderA.setStatus(OrderStatus.BLOQUEADO_DEADLOCK);
                    orderB.setStatus(OrderStatus.BLOQUEADO_DEADLOCK);

                    String message = String.format(
                            "¡INTERBLOQUEO DETECTADO!%n" +
                                    "• El Pedido #%s (%s) retiene [%s] y está BLOQUEADO esperando [%s].%n" +
                                    "• El Pedido #%s (%s) retiene [%s] y está BLOQUEADO esperando [%s].%n" +
                                    "Ambos procesos están en espera circular mutua (Hold-and-Wait).",
                            orderA.getId(), orderA.getMerchandiseType().getDisplayName(),
                            resWaitedByB.getDisplayName(), resWaitedByA.getDisplayName(),
                            orderB.getId(), orderB.getMerchandiseType().getDisplayName(),
                            resWaitedByA.getDisplayName(), resWaitedByB.getDisplayName());

                    DeadlockInfo info = new DeadlockInfo(
                            orderA, orderB, resWaitedByB, resWaitedByA, message, System.currentTimeMillis());
                    this.currentDeadlock = info;

                    // Notificar a observadores registrados (dispara modal en la interfaz)
                    for (DeadlockListener l : listeners) {
                        l.onDeadlockDetected(info);
                    }
                    return info;
                }
            }
        }

        return null;
    }

    /**
     * Resuelve manualmente un interbloqueo aplicando la tecnica de Desalojo Forzoso
     * (Resource Preemption).
     * 
     * Procedimiento de Recuperacion:
     * 
     * - El pedido loser es despojado forzosamente de todos sus recursos retenidos
     * mediante ResourceManager#releaseAllResources(Order).
     * - Los semaforos liberados despiertan al pedido winner, permitiendole avanzar.
     * - El pedido loser regresa a la cola de espera para reintentar su
     * procesamiento mas adelante.
     * - Se restablece la bandera de deadlock y se notifica la resolucion exitosa.
     * 
     * @param winner            Pedido seleccionado para conservar los recursos y
     *                          continuar ejecucion.
     * @param loser             Pedido victima del desalojo que cede sus recursos y
     *                          regresa a la cola.
     * @param reenqueueCallback Funcion de devolucion para reinsertar al pedido
     *                          desalojado en la cola del planificador.
     */
    public synchronized void resolveDeadlockManually(Order winner, Order loser, Consumer<Order> reenqueueCallback) {
        if (currentDeadlock == null)
            return;

        DeadlockInfo resolved = currentDeadlock;

        // 1. Desalojo forzoso de recursos del proceso victima (Preemption)
        resourceManager.releaseAllResources(loser);
        loser.setStatus(OrderStatus.EN_ESPERA_RECURSOS);
        loser.setCurrentPhaseDescription("Reencolado tras resolución de deadlock (cedió recursos)");
        loser.setForceDeadlockTest(false);

        // 2. Reencolar a la victima en el planificador para ser atendido mas adelante
        if (reenqueueCallback != null) {
            reenqueueCallback.accept(loser);
        }

        // 3. Restaurar estado de ejecucion activa para el pedido favorecido
        winner.setStatus(OrderStatus.EN_PROCESO);
        winner.setForceDeadlockTest(false);

        this.currentDeadlock = null;

        // 4. Notificar a los observadores que el deadlock fue resuelto
        for (DeadlockListener l : listeners) {
            l.onDeadlockResolved(resolved);
        }
    }
}
