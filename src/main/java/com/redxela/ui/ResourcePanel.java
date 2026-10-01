package com.redxela.ui;

import com.redxela.concurrency.ResourceManager;
import com.redxela.model.Order;
import com.redxela.model.ResourceType;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Panel visual que muestra el estado y nivel de utilizacion de los recursos compartidos.
 * Incluye barras de progreso con codigo de colores e identificacion de pedidos retenedores.
 */
public class ResourcePanel extends JPanel {

    // Referencia al gestor de semaforos.
    private final ResourceManager resourceManager;

    // Mapeo de barras de progreso por cada recurso fisico.
    private final Map<ResourceType, JProgressBar> progressBars = new EnumMap<>(ResourceType.class);

    // Mapeo de etiquetas con conteo numerico de ocupacion (ej. "3 / 6 en uso").
    private final Map<ResourceType, JLabel> detailLabels = new EnumMap<>(ResourceType.class);

    // Mapeo de etiquetas que listan los pedidos que retienen el recurso.
    private final Map<ResourceType, JLabel> holdersLabels = new EnumMap<>(ResourceType.class);

    /**
     * Construye el panel con una disposicion en cuadricula 2x3 para los 6 recursos.
     *
     * @param resourceManager Gestor de semaforos de recursos.
     */
    public ResourcePanel(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        setLayout(new GridLayout(2, 3, 10, 10));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "ESTADO DE RECURSOS COMPARTIDOS (TIEMPO REAL)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13)
        ));

        for (ResourceType type : ResourceType.values()) {
            JPanel card = createResourceCard(type);
            add(card);
        }
    }

    /**
     * Construye la tarjeta grafica visual para un recurso individual.
     */
    private JPanel createResourceCard(ResourceType type) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel nameLabel = new JLabel(type.getDisplayName());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel countLabel = new JLabel("0 / " + type.getTotalCapacity() + " en uso");
        countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        countLabel.setForeground(Color.LIGHT_GRAY);
        detailLabels.put(type, countLabel);

        JPanel header = new JPanel(new BorderLayout());
        header.add(nameLabel, BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);

        JProgressBar pb = new JProgressBar(0, type.getTotalCapacity());
        pb.setStringPainted(true);
        pb.setValue(0);
        progressBars.put(type, pb);

        JLabel holders = new JLabel("Ocupado por: Ninguno");
        holders.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        holders.setForeground(new Color(180, 180, 180));
        holdersLabels.put(type, holders);

        card.add(header, BorderLayout.NORTH);
        card.add(pb, BorderLayout.CENTER);
        card.add(holders, BorderLayout.SOUTH);

        return card;
    }

    /**
     * Refresca las barras y etiquetas con la informacion actual de los semaforos.
     */
    public void refreshData() {
        for (ResourceType type : ResourceType.values()) {
            int occupied = resourceManager.getOccupiedUnits(type);
            int total = type.getTotalCapacity();
            double pct = (occupied / (double) total) * 100.0;

            JProgressBar pb = progressBars.get(type);
            pb.setValue(occupied);
            pb.setString(String.format("%d / %d (%.0f%%)", occupied, total, pct));

            // Colorear segun nivel de congestion
            if (occupied == total) {
                pb.setForeground(new Color(231, 76, 60)); // Rojo: 100% saturado
            } else if (occupied > total * 0.7) {
                pb.setForeground(new Color(230, 126, 34)); // Naranja: alto uso
            } else {
                pb.setForeground(new Color(46, 204, 113)); // Verde: normal
            }

            detailLabels.get(type).setText(occupied + " / " + total + " en uso");

            Set<Order> holders = resourceManager.getHolders(type);
            if (holders.isEmpty()) {
                holdersLabels.get(type).setText("Ocupado por: Ninguno");
            } else {
                String ids = holders.stream()
                        .map(o -> "#" + o.getId())
                        .limit(4)
                        .collect(Collectors.joining(", "));
                if (holders.size() > 4) ids += " ...";
                holdersLabels.get(type).setText("Ocupado por: " + ids);
            }
        }
    }
}
