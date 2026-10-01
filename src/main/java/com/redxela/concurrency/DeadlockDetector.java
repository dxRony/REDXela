package com.redxela.concurrency;

import com.redxela.model.Order;
import com.redxela.model.OrderStatus;
import com.redxela.model.ResourceType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Detector y Gestor de Interbloqueos (Deadlocks) basado en el Grafo de Asignación de Recursos (RAG).
 * Construye el Grafo de Espera (Wait-For Graph) y detecta ciclos mediante DFS.
 */
public class DeadlockDetector {
    private final ResourceManager resourceManager;
    private final List<DeadlockListener> listeners = new CopyOnWriteArrayList<>();
    private volatile DeadlockInfo currentDeadlock = null;

    public interface DeadlockListener {
        void onDeadlockDetected(DeadlockInfo info);
        void onDeadlockResolved(DeadlockInfo info);
    }

    public record DeadlockInfo(
            Order orderA,
            Order orderB,
            ResourceType resourceA, // Recurso retenido por A y esperado por B
            ResourceType resourceB, // Recurso retenido por B y esperado por A
            String diagnosticMessage,
            long detectedAt
    ) {}

    public DeadlockDetector(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void addListener(DeadlockListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DeadlockListener listener) {
        listeners.remove(listener);
    }

    public DeadlockInfo getCurrentDeadlock() {
        return currentDeadlock;
    }

    /**
     * Inspecciona el Grafo de Asignación de Recursos en busca de ciclos de espera mutua (Deadlock).
     * Retorna DeadlockInfo si encuentra un interbloqueo, o null si el sistema está libre de deadlocks.
     */
    public DeadlockInfo checkForDeadlock() {
        if (currentDeadlock != null) {
            return currentDeadlock; // Ya hay un deadlock activo en resolución
        }

        Map<Order, ResourceType> waitingMap = resourceManager.getOrderWaitingForMap();
        Map<ResourceType, Set<Order>> holdersMap = resourceManager.getResourceHoldersMap();

        // Buscar ciclo de espera mutua entre dos pedidos (P1 espera recurso que tiene P2, y P2 espera recurso que tiene P1)
        for (Map.Entry<Order, ResourceType> entryA : waitingMap.entrySet()) {
            Order orderA = entryA.getKey();
            ResourceType resWaitedByA = entryA.getValue();

            // Quiénes tienen el recurso que espera A?
            Set<Order> holdersOfWaitedRes = holdersMap.getOrDefault(resWaitedByA, Collections.emptySet());

            for (Order orderB : holdersOfWaitedRes) {
                if (orderB.equals(orderA)) continue;

                // Revisar si B está esperando algún recurso retenido por A
                ResourceType resWaitedByB = waitingMap.get(orderB);
                if (resWaitedByB != null && orderA.getAllocatedResources().contains(resWaitedByB)) {
                    // ¡CICLO ENCONTRADO! Deadlock confirmado entre orderA y orderB
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
                            resWaitedByA.getDisplayName(), resWaitedByB.getDisplayName()
                    );

                    DeadlockInfo info = new DeadlockInfo(
                            orderA, orderB, resWaitedByB, resWaitedByA, message, System.currentTimeMillis()
                    );
                    this.currentDeadlock = info;

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
     * Resuelve manualmente el interbloqueo seleccionado por el usuario en la interfaz:
     * El pedido favorecido (winner) continúa con los recursos disputados.
     * El pedido desfavorecido (loser) es desalojado (preempted), libera sus recursos y regresa a la cola de espera.
     */
    public synchronized void resolveDeadlockManually(Order winner, Order loser, Consumer<Order> reenqueueCallback) {
        if (currentDeadlock == null) return;

        DeadlockInfo resolved = currentDeadlock;

        // 1. Desalojar recursos del pedido loser
        resourceManager.releaseAllResources(loser);
        loser.setStatus(OrderStatus.EN_ESPERA_RECURSOS);
        loser.setCurrentPhaseDescription("Reencolado tras resolución de deadlock (cedió recursos)");
        loser.setForceDeadlockTest(false);

        // 2. Reencolar loser para ser atendido más adelante
        if (reenqueueCallback != null) {
            reenqueueCallback.accept(loser);
        }

        // 3. Restaurar estado de winner a activo para que obtenga el recurso liberado
        winner.setStatus(OrderStatus.EN_PROCESO);
        winner.setForceDeadlockTest(false);

        this.currentDeadlock = null;

        for (DeadlockListener l : listeners) {
            l.onDeadlockResolved(resolved);
        }
    }
}
