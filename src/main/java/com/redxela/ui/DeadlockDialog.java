package com.redxela.ui;

import com.redxela.concurrency.DeadlockDetector;
import com.redxela.concurrency.DeadlockInfo;
import com.redxela.model.Order;
import com.redxela.scheduler.OrderScheduler;

import javax.swing.*;
import java.awt.*;

/**
 * Dialogo modal para visualizar y resolver situaciones de interbloqueo (deadlock).
 * Muestra el grafo de asignacion de recursos involucrado y permite seleccionar
 * manualmente que pedido continuara y cual sera desalojado (preemption).
 */
public class DeadlockDialog extends JDialog {

    /**
     * Construye y despliega el dialogo modal de resolucion de Deadlock.
     *
     * @param parent Ventana principal padre.
     * @param info Diagnostico del interbloqueo emitido por el detector.
     * @param detector Instancia del detector para ejecutar la resolucion por desalojo.
     * @param scheduler Planificador para reencolar el pedido victima.
     */
    public DeadlockDialog(JFrame parent, DeadlockInfo info,
                          DeadlockDetector detector, OrderScheduler scheduler) {
        super(parent, "¡ALERTA CRÍTICA: INTERBLOQUEO (DEADLOCK) DETECTADO!", true);
        setSize(700, 480);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(15, 15));

        // 1. Encabezado de Alerta
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(192, 57, 43));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        JLabel titleLbl = new JLabel("⚠️ CONDICIÓN DE INTERBLOQUEO EN RECURSOS (CIRCULAR WAIT)", SwingConstants.CENTER);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(Color.WHITE);
        headerPanel.add(titleLbl, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Cuerpo del Diagnostico
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JTextArea txtDiag = new JTextArea();
        txtDiag.setEditable(false);
        txtDiag.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtDiag.setText(info.diagnosticMessage() + "\n\n"
                + "ANÁLISIS TEÓRICO DE SISTEMAS OPERATIVOS:\n"
                + "─────────────────────────────────────────────────────────────\n"
                + "• Grafo de Asignación de Recursos (RAG): Se detectó un ciclo cerrado de dependencias.\n"
                + "• Condiciones de Coffman presentes:\n"
                + "  1. Exclusión Mutua: Los recursos no pueden compartirse simultáneamente.\n"
                + "  2. Retención y Espera (Hold and Wait): Cada pedido retiene un recurso mientras pide el otro.\n"
                + "  3. No Desalojo (No Preemption): Los recursos no se quitan de forma forzosa automáticamente.\n"
                + "  4. Espera Circular (Circular Wait): Ciclo cerrado de procesos esperando recursos.\n\n"
                + "ACCIÓN REQUERIDA:\n"
                + "Seleccione cuál de los dos pedidos continuará con los recursos disputados.\n"
                + "El otro proceso será DESALOJADO (preempted), liberará sus recursos y regresará a la cola."
        );
        txtDiag.setBackground(new Color(30, 30, 30));
        txtDiag.setForeground(new Color(230, 230, 230));
        contentPanel.add(new JScrollPane(txtDiag), BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        // 3. Botones de Resolucion Manual por Desalojo
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        actionPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));

        Order orderA = info.orderA();
        Order orderB = info.orderB();

        JButton btnFavorecerA = new JButton(String.format(
                "► Favorecer Pedido #%s (%s)  [Desalojar #%s]",
                orderA.getId(), orderA.getMerchandiseType().getDisplayName(), orderB.getId()
        ));
        btnFavorecerA.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnFavorecerA.setBackground(new Color(41, 128, 185));
        btnFavorecerA.setForeground(Color.WHITE);
        btnFavorecerA.addActionListener(e -> {
            detector.resolveDeadlockManually(orderA, orderB, scheduler::reenqueuePreemptedOrder);
            JOptionPane.showMessageDialog(parent,
                    "Deadlock resuelto. El Pedido #" + orderA.getId() + " continúa su procesamiento.\n"
                            + "El Pedido #" + orderB.getId() + " liberó sus recursos y regresó a la cola de espera.",
                    "Resolución Exitosa", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });

        JButton btnFavorecerB = new JButton(String.format(
                "► Favorecer Pedido #%s (%s)  [Desalojar #%s]",
                orderB.getId(), orderB.getMerchandiseType().getDisplayName(), orderA.getId()
        ));
        btnFavorecerB.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnFavorecerB.setBackground(new Color(39, 174, 96));
        btnFavorecerB.setForeground(Color.WHITE);
        btnFavorecerB.addActionListener(e -> {
            detector.resolveDeadlockManually(orderB, orderA, scheduler::reenqueuePreemptedOrder);
            JOptionPane.showMessageDialog(parent,
                    "Deadlock resuelto. El Pedido #" + orderB.getId() + " continúa su procesamiento.\n"
                            + "El Pedido #" + orderA.getId() + " liberó sus recursos y regresó a la cola de espera.",
                    "Resolución Exitosa", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });

        actionPanel.add(btnFavorecerA);
        actionPanel.add(btnFavorecerB);
        add(actionPanel, BorderLayout.SOUTH);
    }
}
