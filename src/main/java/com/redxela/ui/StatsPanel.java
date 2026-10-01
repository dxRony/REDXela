package com.redxela.ui;

import com.redxela.metrics.SimulationMetrics;
import com.redxela.model.enums.ResourceType;
import com.redxela.model.enums.ServiceLevel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

/**
 * Panel de estadisticas y metricas de rendimiento del centro de distribucion.
 * Presenta indicadores de tiempos de espera, cumplimiento de SLA y uso de recursos.
 */
public class StatsPanel extends JPanel {

    // Recolector de metricas de la simulacion.
    private final SimulationMetrics metrics;

    // Etiqueta KPI para pedidos completados.
    private final JLabel lblCompleted;

    // Etiqueta KPI para pedidos en espera.
    private final JLabel lblWaiting;

    // Etiqueta KPI para pedidos en procesamiento activo.
    private final JLabel lblProcessing;

    // Etiqueta KPI para tiempo medio de espera.
    private final JLabel lblAvgWait;

    // Etiqueta KPI para tiempo medio de procesamiento.
    private final JLabel lblAvgProc;

    // Modelo de datos para la tabla de cumplimiento SLA.
    private final DefaultTableModel slaModel;

    // Modelo de datos para la tabla de ocupacion de recursos.
    private final DefaultTableModel resourceModel;

    /**
     * Construye el panel de estadisticas con tarjetas superiores y tablas comparativas.
     *
     * @param metrics Gestor de metricas.
     */
    public StatsPanel(SimulationMetrics metrics) {
        this.metrics = metrics;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Fila Superior de Tarjetas KPI
        JPanel kpiRow = new JPanel(new GridLayout(1, 5, 10, 10));
        lblCompleted = createKpiCard(kpiRow, "PEDIDOS DESPACHADOS", "0", new Color(46, 204, 113));
        lblWaiting = createKpiCard(kpiRow, "PEDIDOS EN ESPERA", "0", new Color(241, 196, 15));
        lblProcessing = createKpiCard(kpiRow, "EN PROCESAMIENTO", "0", new Color(52, 152, 219));
        lblAvgWait = createKpiCard(kpiRow, "TIEMPO PROM. ESPERA", "0.0 s", new Color(155, 89, 182));
        lblAvgProc = createKpiCard(kpiRow, "TIEMPO PROM. PROCESO", "0.0 s", new Color(230, 126, 34));
        add(kpiRow, BorderLayout.NORTH);

        // 2. Tablas Centrales
        JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 10, 10));

        // Tabla SLA vs Tiempo Real
        String[] slaCols = {"Nivel de Servicio", "Tiempo Objetivo (SLA)", "Tiempo Promedio Real", "Cumplimiento"};
        slaModel = new DefaultTableModel(slaCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable slaTable = new JTable(slaModel);
        slaTable.setRowHeight(24);
        JPanel slaWrapper = new JPanel(new BorderLayout());
        slaWrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "DESEMPEÑO POR NIVEL DE SERVICIO (SLA vs REAL)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));
        slaWrapper.add(new JScrollPane(slaTable), BorderLayout.CENTER);
        tablesPanel.add(slaWrapper);

        // Tabla Ocupacion de Recursos
        String[] resCols = {"Recurso Compartido", "Capacidad Total", "Nivel de Ocupación"};
        resourceModel = new DefaultTableModel(resCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable resTable = new JTable(resourceModel);
        resTable.setRowHeight(24);
        JPanel resWrapper = new JPanel(new BorderLayout());
        resWrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "NIVEL DE OCUPACIÓN Y APROVECHAMIENTO DE RECURSOS",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));
        resWrapper.add(new JScrollPane(resTable), BorderLayout.CENTER);
        tablesPanel.add(resWrapper);

        add(tablesPanel, BorderLayout.CENTER);
    }

    /**
     * Construye una tarjeta grafica individual para una metrica KPI.
     */
    private JLabel createKpiCard(JPanel parent, String title, String initialValue, Color accent) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(Color.LIGHT_GRAY);

        JLabel valLbl = new JLabel(initialValue, SwingConstants.CENTER);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valLbl.setForeground(accent);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        parent.add(card);
        return valLbl;
    }

    /**
     * Actualiza los valores de las tarjetas y los registros de las dos tablas de metricas.
     */
    public void refreshData() {
        lblCompleted.setText(String.valueOf(metrics.getCompletedOrdersCount()));
        lblWaiting.setText(String.valueOf(metrics.getWaitingOrdersCount()));
        lblProcessing.setText(String.valueOf(metrics.getProcessingOrdersCount()));
        lblAvgWait.setText(String.format("%.1f s", metrics.getAverageWaitTimeSeconds()));
        lblAvgProc.setText(String.format("%.1f s", metrics.getAverageProcessingTimeSeconds()));

        // Actualizar tabla SLA
        slaModel.setRowCount(0);
        Map<ServiceLevel, Double> waitMap = metrics.getAverageWaitTimeByServiceLevel();
        for (ServiceLevel sl : ServiceLevel.values()) {
            double actualSecs = waitMap.getOrDefault(sl, 0.0);
            String status = actualSecs == 0.0 ? "Sin datos" :
                    (actualSecs <= sl.getMaxWaitSecondsSimulated() ? "✓ Dentro de SLA" : "⚠️ Excedido");
            slaModel.addRow(new Object[]{
                    sl.getDisplayName(),
                    sl.getDescription(),
                    String.format("%.1f seg", actualSecs),
                    status
            });
        }

        // Actualizar tabla de ocupacion de recursos
        resourceModel.setRowCount(0);
        Map<ResourceType, Double> utilMap = metrics.getResourceUtilization();
        for (ResourceType type : ResourceType.values()) {
            double util = utilMap.getOrDefault(type, 0.0);
            resourceModel.addRow(new Object[]{
                    type.getDisplayName(),
                    type.getTotalCapacity() + " unidades",
                    String.format("%.1f%%", util)
            });
        }
    }
}
