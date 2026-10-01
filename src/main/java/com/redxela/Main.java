package com.redxela;

import com.formdev.flatlaf.FlatDarkLaf;
import com.redxela.concurrency.*;
import com.redxela.generator.OrderGenerator;
import com.redxela.metrics.SimulationMetrics;
import com.redxela.model.Catalog;
import com.redxela.model.Product;
import com.redxela.scheduler.OrderScheduler;
import com.redxela.storage.WarehouseStorageManager;
import com.redxela.ui.MainFrame;

import javax.swing.*;

/**
 * Punto de entrada principal para el simulador del Centro de Distribución REDXela.
 * Sistemas Operativos 2 - USAC CUNOC.
 */
public class Main {
    public static void main(String[] args) {
        // 1. Configurar FlatLaf para interfaz moderna tipo Dark Mode
        FlatDarkLaf.setup();

        // 2. Inicializar subsistemas de concurrencia y almacenamiento
        ResourceManager resourceManager = new ResourceManager();
        WarehouseStorageManager storageManager = new WarehouseStorageManager(6, 4, 2, 15); // 48 ranuras
        ReceptionBuffer receptionBuffer = new ReceptionBuffer(15);
        DeadlockDetector deadlockDetector = new DeadlockDetector(resourceManager);

        // Pre-cargar stock inicial en el almacén de forma no contigua para demostración inmediata
        int batchSeed = 100;
        for (Product p : Catalog.getAllProducts()) {
            storageManager.allocateGoods("LOTE-INIT-" + (++batchSeed), p, 20);
        }

        // 3. Iniciar el hilo consumidor de Inventario (Procesamiento de lotes del ReceptionBuffer)
        InventoryProcess inventoryProcess = new InventoryProcess(receptionBuffer, storageManager);
        Thread inventoryThread = new Thread(inventoryProcess, "Thread-Inventory-Consumer");
        inventoryThread.setDaemon(true);
        inventoryThread.start();

        // 4. Iniciar el Productor de Recepción de Mercadería
        ReceptionProcess receptionProcess = new ReceptionProcess(receptionBuffer);

        // 5. Iniciar el Planificador central de pedidos
        OrderScheduler scheduler = new OrderScheduler(resourceManager, storageManager, deadlockDetector);
        scheduler.start();

        // 6. Generador de pedidos automáticos
        OrderGenerator orderGenerator = new OrderGenerator(scheduler);

        // 7. Métricas y estadísticas
        SimulationMetrics metrics = new SimulationMetrics(scheduler, resourceManager);

        // 8. Lanzar la Interfaz Gráfica en el hilo de despacho de eventos de Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(
                    resourceManager,
                    storageManager,
                    scheduler,
                    deadlockDetector,
                    orderGenerator,
                    receptionProcess,
                    metrics
            );
            mainFrame.setVisible(true);
        });
    }
}
