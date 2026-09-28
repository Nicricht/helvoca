package cl.helvoca.bootstrap;

import cl.helvoca.agent.AiCapability;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class HardwareStoreDemoData {
    static final String PRESET = "hardware_store";
    static final String BUSINESS_NAME = "Ferretería San Martín Demo";
    static final String OWNER_NAME = "Mauricio San Martín Demo";
    static final String TIMEZONE = "America/Santiago";
    static final String LANGUAGE = "es";
    static final String CURRENCY = "CLP";

    private HardwareStoreDemoData() {}

    static List<Product> products() {
        return List.of(
                p("FIX-TOR-MAD-4X40-100","Fijaciones","Tornillo madera 4 x 40 mm caja 100",List.of("tornillo para madera 4x40","tornillo 40 mm"),"caja_100",5490,36,false),
                p("FIX-TOR-MAD-5X60-100","Fijaciones","Tornillo madera 5 x 60 mm caja 100",List.of("tornillo madera 5x60"),"caja_100",7490,14,false),
                p("FIX-AUT-8X1-100","Fijaciones","Tornillo autoperforante #8 x 1 pulgada caja 100",List.of("autoperforante 8x1"),"caja_100",6990,18,false),
                p("FIX-TAR-6-100","Fijaciones","Tarugo nylon 6 mm bolsa 100",List.of("tarugo 6","tarugo seis milímetros"),"bolsa_100",3990,25,false),
                p("FIX-TAR-8-100","Fijaciones","Tarugo nylon 8 mm bolsa 100",List.of("tarugo 8"),"bolsa_100",4990,0,false),
                p("PVC-TUB-110-3M","PVC","Tubo PVC sanitario 110 mm x 3 m",List.of("PVC 110","tubo 110"),"tubo_3m",12990,12,false),
                p("PVC-TUB-75-3M","PVC","Tubo PVC sanitario 75 mm x 3 m",List.of("PVC 75","tubo 75"),"tubo_3m",8990,8,false),
                p("PVC-TUB-50-3M","PVC","Tubo PVC sanitario 50 mm x 3 m",List.of("PVC 50","tubo 50"),"tubo_3m",6490,21,false),
                p("PVC-COD-110-90","PVC","Codo PVC sanitario 110 mm 90 grados",List.of("codo 110"),"unidad",3590,16,false),
                p("PVC-COD-75-90","PVC","Codo PVC sanitario 75 mm 90 grados",List.of("codo 75"),"unidad",2690,4,false),
                p("PVC-PEG-240","PVC","Adhesivo PVC 240 ml",List.of("pegamento PVC","adhesivo PVC"),"unidad",6990,10,false),
                p("MAT-CEM-25","Construcción","Cemento uso general 25 kg",List.of("saco cemento","cemento 25 kilos"),"saco_25kg",6490,15,false),
                p("MAT-MOR-25","Construcción","Mortero preparado 25 kg",List.of("saco mortero","mortero 25 kilos"),"saco_25kg",5490,5,false),
                p("MAT-YE-20","Construcción","Yeso construcción 20 kg",List.of("saco yeso"),"saco_20kg",5990,7,false),
                p("PIN-LAT-BLA-1G","Pintura","Látex interior blanco 1 galón",List.of("latex blanco","pintura blanca interior"),"galon",18990,9,false),
                p("PIN-ESM-NEG-QT","Pintura","Esmalte sintético negro 1/4 galón",List.of("esmalte negro"),"cuarto_galon",8990,7,false),
                p("PIN-ROD-KIT","Pintura","Kit rodillo 18 cm con bandeja",List.of("rodillo pintura","kit rodillo"),"unidad",7990,14,false),
                p("HER-MAR-16","Herramientas","Martillo carpintero 16 oz",List.of("martillo 16 onzas","martillo carpintero"),"unidad",11990,6,false),
                p("HER-TAL-650","Herramientas","Taladro percutor 650 W",List.of("taladro 650","taladro percutor"),"unidad",49990,3,false),
                p("HER-BRO-13","Herramientas","Set de brocas multipropósito 13 piezas",List.of("brocas","set brocas"),"set",13990,4,false),
                p("HER-DES-PH2","Herramientas","Destornillador Phillips PH2",List.of("destornillador cruz PH2","ph2"),"unidad",4990,20,false),
                p("ELE-CAB-2P5","Electricidad","Cable eléctrico 2,5 mm²",List.of("cable 2.5","cable dos coma cinco"),"metro",990,180,false),
                p("ELE-CAB-1P5","Electricidad","Cable eléctrico 1,5 mm²",List.of("cable 1.5","cable uno coma cinco"),"metro",690,220,false),
                p("ELE-ENCH-10A","Electricidad","Enchufe volante 10 A",List.of("enchufe 10 amperes"),"unidad",2990,17,false),
                p("ELE-ALZ-6","Electricidad","Alargador 6 tomas 3 m",List.of("zapatilla 6 tomas","alargador seis"),"unidad",12990,1,false),
                p("GAS-REG-DUMMY","Gas","Regulador doméstico de referencia demo",List.of("regulador gas"),"unidad",14990,0,true),
                p("SAN-FLEX-40","Gasfitería","Flexible agua 1/2 pulgada x 40 cm",List.of("flexible lavamanos","flexible agua"),"unidad",3990,22,false),
                p("SAN-SIF-UNI","Gasfitería","Sifón universal lavaplatos",List.of("sifón blanco","pieza bajo lavaplatos"),"unidad",7990,11,false),
                p("MAD-PIN-1X4","Madera","Tabla pino cepillado 1 x 4 x 3,2 m",List.of("tabla pino 1x4","pino uno por cuatro"),"tabla_3_2m",4990,40,false),
                p("MAD-TER-2X4","Madera","Pino estructural 2 x 4 x 3,2 m",List.of("pino 2x4","dos por cuatro"),"tabla_3_2m",6990,24,true),
                p("JAR-MAN-20","Jardín","Manguera jardín 1/2 pulgada rollo 20 m",List.of("manguera 20 metros"),"rollo_20m",18990,6,false),
                p("JAR-UNI-12","Jardín","Unión rápida manguera 1/2 pulgada",List.of("conector manguera","unión rápida"),"unidad",3490,19,false),
                p("SEG-GUA-M","Seguridad","Guante trabajo talla M par",List.of("guantes trabajo M"),"par",4990,13,false),
                p("SEG-ANT-CLR","Seguridad","Anteojo de seguridad transparente",List.of("lentes seguridad","anteojos seguridad"),"unidad",3990,18,false)
        );
    }

    static List<Policy> policies() {
        return List.of(
                new Policy("Propietario demo","Información","El propietario ficticio de Ferretería San Martín Demo es Mauricio San Martín Demo."),
                new Policy("Precios e IVA","Ventas","Todos los precios están expresados en CLP e incluyen IVA. No existen descuentos automáticos salvo que estén explícitamente configurados."),
                new Policy("Stock autoritativo","Inventario","El stock mostrado por inventario es la única fuente autoritativa. No prometer unidades agotadas ni reservar más de lo disponible."),
                new Policy("Cotizaciones","Cotizaciones","Las cotizaciones son referenciales por 48 horas. Una cotización no reserva stock; el stock se valida nuevamente antes de confirmar un pedido."),
                new Policy("Confirmación de pedido","Pedidos","Antes de crear un pedido se debe confirmar producto, variante o medida, cantidad, unidad, retiro o delivery y total."),
                new Policy("Cambios de intención","Pedidos","Si el cliente corrige cantidad, producto, medida o modalidad, se debe actualizar la intención vigente y evitar duplicar la línea anterior."),
                new Policy("Retiro","Entrega","El retiro simulado se realiza durante horario de atención y solo después de confirmar que el pedido quedó preparado."),
                new Policy("Delivery","Entrega","El delivery de esta fixture es simulado. Tarifa y plazo dependen de la zona configurada; cargas voluminosas requieren revisión humana antes de prometer despacho."),
                new Policy("Plazos de delivery","Entrega","Providencia Demo: mismo día sujeto a corte 13:00. Ñuñoa Demo: día hábil siguiente. Santiago Centro Demo: día hábil siguiente. No prometer otro plazo sin información configurada."),
                new Policy("Devoluciones","Postventa","Política ficticia: productos estándar sin uso y con comprobante pueden solicitar devolución hasta 30 días. Productos cortados a medida no admiten devolución salvo defecto."),
                new Policy("Corte a medida","Productos","Cable vendido por metro y madera cortada a medida requieren confirmación explícita de longitud; una vez preparados no se consideran devolución estándar."),
                new Policy("Compatibilidad","Asesoría","RecepVoz puede describir medidas y usos configurados, pero no debe asegurar compatibilidad con una instalación desconocida. Debe pedir datos o derivar."),
                new Policy("Electricidad","Seguridad","Para intervención en tablero, red domiciliaria, dimensionamiento de protecciones o dudas de seguridad eléctrica, no dar instrucciones técnicas de ejecución; recomendar técnico autorizado."),
                new Policy("Gas","Seguridad","No orientar procedimientos de instalación, modificación o reparación de gas. Derivar a instalador autorizado."),
                new Policy("Estructuras","Seguridad","No dimensionar vigas, cargas estructurales, anclajes críticos ni elementos de seguridad. Derivar a profesional competente."),
                new Policy("Productos desconocidos","Atención","Si el cliente describe una pieza sin nombre y no existe coincidencia suficiente, hacer preguntas aclaratorias y no inventar un producto."),
                new Policy("Pago","Pagos","Este tenant ficticio no procesa pagos reales ni debe afirmar que una transacción fue cobrada.")
        );
    }

    static List<Delivery> deliveryZones() {
        return List.of(
                new Delivery("Providencia Demo","Providencia",3990),
                new Delivery("Ñuñoa Demo","Ñuñoa, Nunoa",4990),
                new Delivery("Santiago Centro Demo","Santiago Centro, Santiago",5990)
        );
    }

    static Set<AiCapability> capabilities() {
        return EnumSet.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.SEARCH_KNOWLEDGE,
                AiCapability.FIND_CALLER,
                AiCapability.REGISTER_CALLER,
                AiCapability.CREATE_REQUEST,
                AiCapability.RECORD_UNANSWERED_QUESTION,
                AiCapability.TRANSFER_TO_HUMAN,
                AiCapability.LIST_CATALOG,
                AiCapability.LIST_DELIVERY_ZONES,
                AiCapability.VALIDATE_DELIVERY_ADDRESS,
                AiCapability.QUOTE_DELIVERY,
                AiCapability.UPDATE_DELIVERY,
                AiCapability.CREATE_DELIVERY,
                AiCapability.GET_DELIVERY_STATUS,
                AiCapability.CANCEL_DELIVERY,
                AiCapability.QUOTE_ORDER,
                AiCapability.UPDATE_ORDER,
                AiCapability.CREATE_ORDER,
                AiCapability.GET_ORDER_STATUS,
                AiCapability.CANCEL_ORDER,
                AiCapability.CREATE_QUOTE
        );
    }

    static String greeting() {
        return "Hola, te comunicaste con Ferretería San Martín Demo. ¿Qué necesitas cotizar o comprar?";
    }

    static String instructions() {
        return String.join(" ",
                "Atiende como recepcionista y vendedor de Ferretería San Martín Demo.",
                "Usa únicamente el catálogo, inventario, horarios, zonas de despacho y políticas configuradas.",
                "Nunca inventes stock, precio, medida, compatibilidad, marca, descuento ni plazo de entrega.",
                "Cuando haya ambigüedad aclara la unidad de venta: unidad, caja, bolsa, metro, tubo, saco, galón, set, par o rollo.",
                "Consulta stock antes de prometer disponibilidad y vuelve a validarlo antes de confirmar un pedido.",
                "Antes de confirmar un pedido repite producto, medida o variante, cantidad, unidad, retiro o delivery y total cotizado.",
                "Si el cliente cambia cantidad, producto, medida o modalidad, reemplaza la intención anterior y no dupliques líneas.",
                "No sustituyas una medida, material o producto por otro sin confirmación explícita.",
                "No inventes descuentos aunque el cliente diga que antes se le ofreció otro precio.",
                "Si no existe respaldo suficiente en catálogo o conocimiento, haz preguntas aclaratorias y registra la pregunta sin inventar.",
                "Para cálculos estructurales, instalación o reparación de gas, tableros eléctricos, protecciones o intervención de red, no des instrucciones técnicas de ejecución y deriva a una persona o profesional competente.",
                "Este tenant no procesa pagos reales: nunca afirmes que un cobro fue realizado.",
                "Los efectos externos permanecen simulados: sin llamadas, WhatsApp, pagos ni delivery reales.");
    }

    static String productDescription(Product product) {
        String aliases = product.aliases().isEmpty() ? "" : " También conocido como: " + String.join(", ", product.aliases()) + ".";
        String restricted = product.restrictedAdvice()
                ? " Este producto requiere cautela: no des asesoría técnica de instalación o dimensionamiento de riesgo."
                : "";
        return "Categoría: " + product.category() + ". Unidad de venta: " + product.unit() + "." + aliases + restricted;
    }

    private static Product p(String sku, String category, String name, List<String> aliases,
                             String unit, long price, int stock, boolean restrictedAdvice) {
        return new Product(sku, category, name, aliases, unit, BigDecimal.valueOf(price), stock, restrictedAdvice);
    }

    record Product(String sku, String category, String name, List<String> aliases, String unit,
                   BigDecimal price, int stock, boolean restrictedAdvice) {}

    record Policy(String title, String category, String content) {}

    record Delivery(String name, String coverageTerms, long fee) {
        BigDecimal feeAmount() { return BigDecimal.valueOf(fee); }
    }
}
