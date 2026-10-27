package com.redxela.ui;

import com.redxela.metrics.SimulationMetrics;

import javax.swing.*;
import java.awt.*;

/**
 * Panel de estadisticas y metricas de rendimiento del centro de distribucion.
 * Presenta tarjetas resumen de pedidos despachados, en cola, activos y tiempos promedio.
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

    /**
     * Construye una barra horizontal de 5 tarjetas KPI de desempeno en tiempo real.
     *
     * @param metrics Gestor de metricas de simulacion.
     */
    public StatsPanel(SimulationMetrics metrics) {
        this.metrics = metrics;
        setLayout(new GridLayout(1, 5, 8, 8));
        setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        lblCompleted = createKpiCard("PEDIDOS DESPACHADOS", "0", new Color(46, 204, 113));
        lblWaiting = createKpiCard("PEDIDOS EN ESPERA", "0", new Color(241, 196, 15));
        lblProcessing = createKpiCard("EN PROCESAMIENTO", "0", new Color(52, 152, 219));
        lblAvgWait = createKpiCard("TIEMPO PROM. ESPERA", "0.0 s", new Color(155, 89, 182));
        lblAvgProc = createKpiCard("TIEMPO PROM. PROCESO", "0.0 s", new Color(230, 126, 34));
    }

    /**
     * Construye una tarjeta grafica individual para una metrica KPI.
     */
    private JLabel createKpiCard(String title, String initialValue, Color accent) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleLbl.setForeground(Color.LIGHT_GRAY);

        JLabel valLbl = new JLabel(initialValue, SwingConstants.CENTER);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valLbl.setForeground(accent);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        add(card);
        return valLbl;
    }

    /**
     * Actualiza los valores de las tarjetas KPI en vivo.
     */
    public void refreshData() {
        lblCompleted.setText(String.valueOf(metrics.getCompletedOrdersCount()));
        lblWaiting.setText(String.valueOf(metrics.getWaitingOrdersCount()));
        lblProcessing.setText(String.valueOf(metrics.getProcessingOrdersCount()));
        lblAvgWait.setText(String.format("%.1f s", metrics.getAverageWaitTimeSeconds()));
        lblAvgProc.setText(String.format("%.1f s", metrics.getAverageProcessingTimeSeconds()));
    }
}
