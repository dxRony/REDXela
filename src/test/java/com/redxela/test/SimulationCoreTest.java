package com.redxela.test;

import com.redxela.concurrency.DeadlockDetector;
import com.redxela.concurrency.ReceptionBuffer;
import com.redxela.concurrency.ResourceManager;
import com.redxela.model.*;
import com.redxela.scheduler.SjfPolicy;
import com.redxela.scheduler.StrictPriorityPolicy;
import com.redxela.storage.WarehouseStorageManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para validar los algoritmos y mecanismos centrales de Sistemas Operativos 2:
 * Semáforos, Productor-Consumidor, Almacenamiento no contiguo, Planificadores y Deadlock.
 */
public class SimulationCoreTest {

    @Test
    @DisplayName("Validar capacidades de semáforos de recursos compartidos")
    void testResourceManagerCapacities() {
        ResourceManager rm = new ResourceManager();
        assertEquals(6, rm.getAvailableUnits(ResourceType.PACKING_STATION));
        assertEquals(2, rm.getAvailableUnits(ResourceType.LOADING_DOCK));
        assertEquals(10, rm.getAvailableUnits(ResourceType.WAREHOUSE_WORKER));
        assertEquals(3, rm.getAvailableUnits(ResourceType.FORKLIFT));
        assertEquals(4, rm.getAvailableUnits(ResourceType.QUALITY_STATION));
        assertEquals(1, rm.getAvailableUnits(ResourceType.BARCODE_SCANNER));
    }

    @Test
    @DisplayName("Validar buffer acotado Productor-Consumidor con Semáforos")
    void testReceptionBufferProducerConsumer() throws InterruptedException {
        ReceptionBuffer buffer = new ReceptionBuffer(5);
        Product prod = Catalog.getAllProducts().get(0);
        GoodsArrival arrival = new GoodsArrival("LOT-01", prod, 20, System.currentTimeMillis());

        buffer.produce(arrival);
        assertEquals(1, buffer.getCurrentSize());

        GoodsArrival consumed = buffer.consume();
        assertEquals(arrival.batchId(), consumed.batchId());
        assertEquals(0, buffer.getCurrentSize());
    }

    @Test
    @DisplayName("Validar almacenamiento físico con asignación no contigua (Paginación)")
    void testNonContiguousStorageAllocation() {
        WarehouseStorageManager storage = new WarehouseStorageManager(3, 2, 2, 10); // 12 slots, 10 unid/slot
        Product prod = Catalog.getAllProducts().get(0);

        // Almacenar 25 unidades: debe ocupar 3 slots distintos (10 + 10 + 5)
        List<WarehouseSlot> allocated = storage.allocateGoods("LOTE-TEST-1", prod, 25);
        assertEquals(3, allocated.size());
        assertEquals(25, storage.getAvailableStock(prod));

        // Consumir 15 unidades: debe liberar el primer slot completamente
        boolean consumed = storage.consumeStock(prod, 15);
        assertTrue(consumed);
        assertEquals(10, storage.getAvailableStock(prod));
    }

    @Test
    @DisplayName("Validar políticas de planificación (Prioridad Estricta vs SJF)")
    void testSchedulingPolicies() {
        Product prod = Catalog.getAllProducts().get(0);
        Map<Product, Integer> smallItems = Map.of(prod, 2);
        Map<Product, Integer> largeItems = Map.of(prod, 10);

        Order expLarge = new Order("1", "Cliente A", ServiceLevel.EXPRES, MerchandiseType.ESTANDAR, largeItems);
        Order ecoSmall = new Order("2", "Cliente B", ServiceLevel.ECONOMICO, MerchandiseType.ESTANDAR, smallItems);

        // Política de Prioridad Estricta: Nivel 1 (Exprés) gana sin importar cantidad
        StrictPriorityPolicy priorityPolicy = new StrictPriorityPolicy();
        assertTrue(priorityPolicy.getComparator().compare(expLarge, ecoSmall) < 0);

        // Política SJF (Menor Cantidad de Artículos): ecoSmall (2 arts) gana a expLarge (10 arts)
        SjfPolicy sjfPolicy = new SjfPolicy();
        assertTrue(sjfPolicy.getComparator().compare(ecoSmall, expLarge) < 0);
    }

    @Test
    @DisplayName("Validar detección y resolución manual de Deadlock en el grafo RAG")
    void testDeadlockDetectionAndResolution() {
        ResourceManager rm = new ResourceManager();
        DeadlockDetector detector = new DeadlockDetector(rm);

        Product prodFragil = Catalog.getByMerchandiseType(MerchandiseType.FRAGIL).get(0);
        Product prodPesado = Catalog.getByMerchandiseType(MerchandiseType.PESADA).get(0);

        Order orderA = new Order("A", "Fragil", ServiceLevel.EXPRES, MerchandiseType.FRAGIL, Map.of(prodFragil, 1));
        Order orderB = new Order("B", "Pesada", ServiceLevel.EXPRES, MerchandiseType.PESADA, Map.of(prodPesado, 1));

        // Registrar en ResourceManager las adquisiciones de recursos
        try {
            rm.acquireResource(orderA, ResourceType.QUALITY_STATION, 1);
            rm.acquireResource(orderB, ResourceType.FORKLIFT, 1);
        } catch (InterruptedException ignored) {}

        // Registrar aristas de espera mutua (A espera Montacargas y B espera Calidad)
        rm.registerWaiting(orderA, ResourceType.FORKLIFT);
        rm.registerWaiting(orderB, ResourceType.QUALITY_STATION);

        // Ejecutar detector
        DeadlockDetector.DeadlockInfo deadlock = detector.checkForDeadlock();
        assertNotNull(deadlock, "Debe detectar el ciclo de interbloqueo mutuo");
        assertEquals(OrderStatus.BLOQUEADO_DEADLOCK, orderA.getStatus());
        assertEquals(OrderStatus.BLOQUEADO_DEADLOCK, orderB.getStatus());

        // Resolver manualmente: favorecer a OrderA
        AtomicBoolean loserPreempted = new AtomicBoolean(false);
        detector.resolveDeadlockManually(orderA, orderB, loser -> loserPreempted.set(true));

        assertNull(detector.getCurrentDeadlock(), "El deadlock debe marcarse como resuelto");
        assertTrue(loserPreempted.get(), "OrderB debe ser desalojado y reencolado");
        assertEquals(OrderStatus.EN_PROCESO, orderA.getStatus());
    }
}
