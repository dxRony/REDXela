package com.redxela.ui;

import com.redxela.concurrency.DeadlockDetector;
import com.redxela.concurrency.ReceptionProcess;
import com.redxela.concurrency.ResourceManager;
import com.redxela.generator.OrderGenerator;
import com.redxela.metrics.SimulationMetrics;
import com.redxela.scheduler.*;
import com.redxela.storage.WarehouseStorageManager;

import javax.swing.*;
import java.awt.*;

/**
 * Ventana Principal de la simulación del Centro de Distribución REDXela.
 */
public class MainFrame extends JFrame {
    private final OrderScheduler scheduler;
    private final OrderGenerator orderGenerator;
    private final ReceptionProcess receptionProcess;

    // Paneles hijos
    private final ResourcePanel resourcePanel;
    private final OrdersTablePanel ordersTablePanel;
    private final WarehouseViewPanel warehouseViewPanel;
    private final StatsPanel statsPanel;

    private final Timer refreshTimer;

    public MainFrame(ResourceManager resourceManager,
                     WarehouseStorageManager storageManager,
                     OrderScheduler scheduler,
                     DeadlockDetector deadlockDetector,
                     OrderGenerator orderGenerator,
                     ReceptionProcess receptionProcess,
                     SimulationMetrics metrics) {
        super("REDXela - Centro de Distribución de Comercio Electrónico (Sistemas Operativos 2)");       
        this.scheduler = scheduler;
        this.orderGenerator = orderGenerator;
        this.receptionProcess = receptionProcess;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1380, 860);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // 1. Barra de Herramientas Superior
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        // 2. Paneles de Pestañas
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Pestaña 1: Operaciones y Recursos
        JPanel opsPanel = new JPanel(new BorderLayout(5, 8));
        opsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.resourcePanel = new ResourcePanel(resourceManager);
        this.ordersTablePanel = new OrdersTablePanel(scheduler);
        opsPanel.add(resourcePanel, BorderLayout.NORTH);
        opsPanel.add(ordersTablePanel, BorderLayout.CENTER);
        tabbedPane.addTab("📦 Monitoreo de Operaciones y Recursos", opsPanel);

        // Pestaña 2: Almacén Físico No Contiguo
        this.warehouseViewPanel = new WarehouseViewPanel(storageManager);
        tabbedPane.addTab("🏢 Almacén Físico (Asignación No Contigua)", warehouseViewPanel);

        // Pestaña 3: Estadísticas y Métricas
        this.statsPanel = new StatsPanel(metrics);
        tabbedPane.addTab("📊 Estadísticas y Políticas de Planificación", statsPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // 3. Listener de Deadlocks: abre el diálogo inmediatamente
        deadlockDetector.addListener(new DeadlockDetector.DeadlockListener() {
            @Override
            public void onDeadlockDetected(DeadlockDetector.DeadlockInfo info) {
                SwingUtilities.invokeLater(() -> {
                    DeadlockDialog dlg = new DeadlockDialog(MainFrame.this, info, deadlockDetector, scheduler);
                    dlg.setVisible(true);
                });
            }

            @Override
            public void onDeadlockResolved(DeadlockDetector.DeadlockInfo info) {
                SwingUtilities.invokeLater(() -> refreshAllPanels());
            }
        });

        // 4. Timer de refresco periódico de la interfaz (cada 300 ms)
        refreshTimer = new Timer(300, e -> refreshAllPanels());
        refreshTimer.start();
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        // Selector de Política de Planificación
        bar.add(new JLabel("Política de Atención:"));
        SchedulingPolicy[] policies = {
                new StrictPriorityPolicy(),
                new AgingPriorityPolicy(),
                new SjfPolicy()
        };
        JComboBox<SchedulingPolicy> cmbPolicy = new JComboBox<>(policies);
        cmbPolicy.setSelectedIndex(0);
        cmbPolicy.addActionListener(e -> {
            SchedulingPolicy p = (SchedulingPolicy) cmbPolicy.getSelectedItem();
            if (p != null) {
                scheduler.setPolicy(p);
            }
        });
        bar.add(cmbPolicy);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        // Controles de Generador Automático
        JButton btnToggleGen = new JButton("Iniciar Generador Automático");
        btnToggleGen.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnToggleGen.setBackground(new Color(41, 128, 185));
        btnToggleGen.setForeground(Color.WHITE);
        btnToggleGen.addActionListener(e -> {
            if (orderGenerator.isRunning()) {
                orderGenerator.stop();
                btnToggleGen.setText("Iniciar Generador Automático");
                btnToggleGen.setBackground(new Color(41, 128, 185));
            } else {
                orderGenerator.start(3); // Cada 3 segundos
                btnToggleGen.setText("⏸ Detener Generador");
                btnToggleGen.setBackground(new Color(192, 57, 43));
            }
        });
        bar.add(btnToggleGen);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        // Botón Pedido Manual
        JButton btnManualOrder = new JButton("+ Pedido Manual");
        btnManualOrder.addActionListener(e -> {
            ManualOrderDialog dlg = new ManualOrderDialog(this, scheduler);
            dlg.setVisible(true);
        });
        bar.add(btnManualOrder);

        // Botón Recepción de Mercadería Manual
        JButton btnGoods = new JButton("+ Recepción Camión (Stock)");
        btnGoods.addActionListener(e -> {
            ManualGoodsDialog dlg = new ManualGoodsDialog(this, receptionProcess);
            dlg.setVisible(true);
        });
        bar.add(btnGoods);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        // Botón Provocar Deadlock
        JButton btnDeadlock = new JButton("⚠️ PROVOCAR DEADLOCK");
        btnDeadlock.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDeadlock.setBackground(new Color(192, 57, 43));
        btnDeadlock.setForeground(Color.WHITE);
        btnDeadlock.setToolTipText("Inyecta simultáneamente Pedido Frágil y Pedido Pesado solicitando recursos en orden inverso");
        btnDeadlock.addActionListener(e -> {
            orderGenerator.triggerDeadlockScenario();
            JOptionPane.showMessageDialog(this,
                    "Se han inyectado dos pedidos en conflicto inverso de recursos:\n"
                            + "1. Pedido Frágil (Control Calidad -> Montacargas)\n"
                            + "2. Pedido Pesado (Montacargas -> Control Calidad)\n"
                            + "En breve el detector RAG reportará el interbloqueo.",
                    "Escenario de Deadlock Activado",
                    JOptionPane.WARNING_MESSAGE);
        });
        bar.add(btnDeadlock);

        return bar;
    }

    private void refreshAllPanels() {
        resourcePanel.refreshData();
        ordersTablePanel.refreshData();
        warehouseViewPanel.refreshData();
        statsPanel.refreshData();
    }
}
