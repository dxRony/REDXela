package com.redxela.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo predefinido de productos de REDXela.
 */
public class Catalog {
    private static final List<Product> PRODUCTS = new ArrayList<>();

    static {
        // Estándar (General / Retail)
        PRODUCTS.add(new Product("EST-01", "Libro de Algoritmos y SO", MerchandiseType.ESTANDAR, 10));
        PRODUCTS.add(new Product("EST-02", "Ropa Deportiva / Camisetas", MerchandiseType.ESTANDAR, 15));
        PRODUCTS.add(new Product("EST-03", "Zapatos Casuales de Cuero", MerchandiseType.ESTANDAR, 8));

        // Frágil (Electrónica / Cristal)
        PRODUCTS.add(new Product("FRG-01", "Smartphone 5G Pantalla Curva", MerchandiseType.FRAGIL, 6));
        PRODUCTS.add(new Product("FRG-02", "Smart TV 55 Pulgadas OLED", MerchandiseType.FRAGIL, 2));
        PRODUCTS.add(new Product("FRG-03", "Juego de Copas de Cristal Fino", MerchandiseType.FRAGIL, 4));

        // Pesada (Voluminosa / Maquinaria)
        PRODUCTS.add(new Product("PES-01", "Refrigeradora Industrial Doble Puerta", MerchandiseType.PESADA, 1));
        PRODUCTS.add(new Product("PES-02", "Generador Eléctrico a Diésel", MerchandiseType.PESADA, 1));
        PRODUCTS.add(new Product("PES-03", "Lavadora Automática 22kg", MerchandiseType.PESADA, 1));

        // Alto Valor (Restringida / Auditoría)
        PRODUCTS.add(new Product("VAL-01", "Reloj de Lujo de Edición Limitada", MerchandiseType.ALTO_VALOR, 5));
        PRODUCTS.add(new Product("VAL-02", "Medicamentos Oncológicos Especiales", MerchandiseType.ALTO_VALOR, 10));
        PRODUCTS.add(new Product("VAL-03", "Dron Profesional con Cámara Térmica", MerchandiseType.ALTO_VALOR, 3));

        // Perecedera (Cadena de Frío)
        PRODUCTS.add(new Product("PER-01", "Lote de Vacunas Termosensibles", MerchandiseType.PERECEDERA, 8));
        PRODUCTS.add(new Product("PER-02", "Salmón Fresco para Exportación", MerchandiseType.PERECEDERA, 5));
        PRODUCTS.add(new Product("PER-03", "Fresas Orgánicas Seleccionadas", MerchandiseType.PERECEDERA, 12));
    }

    public static List<Product> getAllProducts() {
        return Collections.unmodifiableList(PRODUCTS);
    }

    public static Product findById(String id) {
        return PRODUCTS.stream()
                .filter(p -> p.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public static List<Product> getByMerchandiseType(MerchandiseType type) {
        return PRODUCTS.stream()
                .filter(p -> p.getMerchandiseType() == type)
                .toList();
    }
}
