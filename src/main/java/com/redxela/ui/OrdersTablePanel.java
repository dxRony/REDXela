package com.redxela.ui;

import com.redxela.model.Order;
import com.redxela.model.ResourceType;
import com.redxela.scheduler.OrderScheduler;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Panel con tablas sincronizadas que muestran los pedidos en procesamiento activo
 * y la cola de espera organizada según la política activa.
 */
public class OrdersTablePanel extends JPanel {
    private final OrderScheduler scheduler;

    private final DefaultTableModel processingModel;
    private final JTable processingTable;

    private final DefaultTableModel waitingModel;
    private final JTable waitingTable;

    public OrdersTablePanel(OrderScheduler scheduler) {
        this.scheduler = scheduler;
        setLayout(new GridLayout(2, 1, 10, 10));

        // 1. Tabla de Pedidos en Procesamiento Activo
        String[] procCols = {"ID", "Cliente", "SLA", "Tipo Mercancía", "Artículos", "Progreso", "Fase Actual / Recurso"};
        processingModel = new DefaultTableModel(procCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        processingTable = new JTable(processingModel);
        processingTable.setRowHeight(26);
        setupColumnStyles(processingTable);

        JPanel procPanel = new JPanel(new BorderLayout());
        procPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "PEDIDOS EN PROCESAMIENTO ACTIVO (HILOS EN EJECUCIÓN)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));
        procPanel.add(new JScrollPane(processingTable), BorderLayout.CENTER);

        // 2. Tabla de Cola de Espera
        String[] waitCols = {"Pos.", "ID", "Cliente", "Nivel SLA", "Tipo Mercancía", "Arts.", "Tiempo Espera", "Prioridad", "Estado"};
        waitingModel = new DefaultTableModel(waitCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        waitingTable = new JTable(waitingModel);
        waitingTable.setRowHeight(24);
        setupColumnStyles(waitingTable);

        JPanel waitPanel = new JPanel(new BorderLayout());
        waitPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "COLA DE ESPERA (ORDENADA SEGÚN POLÍTICA ACTIVA)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));
        waitPanel.add(new JScrollPane(waitingTable), BorderLayout.CENTER);

        add(procPanel);
        add(waitPanel);
    }

    private void setupColumnStyles(JTable table) {
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.setDefaultRenderer(Object.class, centerRenderer);
    }

    public void refreshData() {
        // Actualizar pedidos en proceso
        List<Order> processing = scheduler.getProcessingOrders();
        processingModel.setRowCount(0);
        for (Order o : processing) {
            String resources = o.getAllocatedResources().stream()
                    .map(ResourceType::getDisplayName)
                    .collect(Collectors.joining(", "));
            if (resources.isEmpty()) resources = "Adquiriendo...";

            processingModel.addRow(new Object[]{
                    "#" + o.getId(),
                    o.getClientName(),
                    o.getServiceLevel().getDisplayName(),
                    o.getMerchandiseType().getDisplayName(),
                    o.getTotalUnits(),
                    o.getProgressPercent() + "%",
                    o.getCurrentPhaseDescription() + " [" + resources + "]"
            });
        }

        // Actualizar cola de espera
        List<Order> waiting = scheduler.getWaitingQueue();
        waitingModel.setRowCount(0);
        long now = System.currentTimeMillis();
        int pos = 1;
        for (Order o : waiting) {
            long waitSecs = o.getTotalWaitTimeMillis() / 1000;
            String priorityStr = String.format("%.1f pts", o.calculateAgingPriority(now));

            waitingModel.addRow(new Object[]{
                    pos++,
                    "#" + o.getId(),
                    o.getClientName(),
                    o.getServiceLevel().getDisplayName(),
                    o.getMerchandiseType().getDisplayName(),
                    o.getTotalUnits(),
                    waitSecs + " s",
                    priorityStr,
                    o.getStatus().getLabel()
            });
        }
    }
}
