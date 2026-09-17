# Helvoca frontend operational redesign

Fecha: 2026-09-17

## Objetivo

Convertir Helvoca en una experiencia orientada al trabajo diario del negocio, no a la configuración técnica. El usuario debe entender al entrar: qué pasó hoy, qué necesita atención, qué reservas/pedidos/clientes existen y de qué conversación nació cada operación.

## Principios

- Inicio responde “qué está pasando ahora”.
- Conversaciones responde “qué dijeron los clientes”.
- Operaciones responde “qué hizo Helvoca y cuál es el historial operativo”.
- Configuración responde “cómo quiero que trabaje Helvoca”.
- Diagnóstico técnico queda fuera del flujo principal.
- No mostrar códigos internos cuando exista una etiqueta humana.
- No duplicar datos entre pantallas si basta con enlazar al detalle.
- Mantener arquitectura universal por capacidades, sin condiciones por industria.
- Voz y WhatsApp comparten el mismo modelo de presentación cuando sea posible.

## Navegación final

- Inicio
- Conversaciones
- Operaciones
- Configuración
- Probar recepcionista como acción secundaria

## 1. Inicio

Inicio se convierte en el centro operacional principal.

Contenido:

1. Estado general: “Helvoca está atendiendo”.
2. Métricas reales:
   - llamadas hoy;
   - WhatsApp hoy;
   - reservas hoy;
   - clientes nuevos;
   - solicitudes abiertas;
   - preguntas pendientes.
3. Workspace de negocio:
   - Reservas;
   - Pedidos;
   - Solicitudes;
   - Clientes.
4. Actividad reciente con enlace a Conversaciones.

El workspace que hoy vive en Operaciones se mueve a Inicio. No se duplica.

## 2. Reservas

### Tabla desktop

Columnas:
- Fecha / hora
- Cliente
- Servicio
- Contacto
- Origen
- Estado

### Filtros

Barra compacta sobre la tabla:
- búsqueda por cliente, teléfono o servicio;
- fecha: todas, hoy, mañana, esta semana, próximas, pasadas;
- servicio dinámico desde catálogo;
- estado;
- origen;
- limpiar filtros;
- contador “Mostrando X de Y”.

En móvil los filtros se agrupan en un control compacto y las filas se muestran como tarjetas.

### Detalle de reserva

Al seleccionar una reserva se abre un panel lateral con:

- cliente;
- teléfono;
- servicio;
- fecha/hora;
- estado;
- origen;
- notas;
- resumen de la conversación de origen;
- transcripción completa si provino de voz;
- mensajes completos si provino de WhatsApp;
- acciones realizadas por Helvoca;
- historial de cambios de la reserva;
- enlace “Ver conversación completa”.

### Relación con la conversación

No se infiere por teléfono o cercanía temporal.

Voz:
- usar CallAction.entityType/entityId para localizar la llamada que creó/modificó la reserva;
- reutilizar el detalle de llamada existente para resumen, acciones y transcripción.

WhatsApp:
- usar BusinessOperation/BusinessOperationEvent;
- operationId de la reserva y sourceReferenceId del evento para localizar la conversación de origen;
- reutilizar la API de mensajes de conversación.

Si la reserva es manual, mostrar “Creada manualmente” y no inventar conversación.

## 3. Pedidos

Desktop usa tabla compacta:
- pedido;
- cliente;
- total;
- entrega;
- estado;
- origen.

El detalle lateral mantiene:
- cliente y contacto;
- líneas del pedido;
- total;
- dirección;
- estado;
- acciones permitidas por backend;
- conversación de origen cuando exista.

## 4. Solicitudes y Clientes

Se mantienen dentro del workspace principal de Inicio.

Solicitudes:
- pendientes primero;
- lenguaje humano;
- creación y resolución sin exponer estados internos innecesarios.

Clientes:
- nombre;
- teléfono;
- email;
- fecha de registro;
- acceso posterior a historial de interacciones.

## 5. Configuración

Configuración deja de ser una sección embebida debajo de Inicio.

Nueva pantalla propia: /settings.html

Secciones:
- General / Negocio
- Recepcionista
- Servicios
- Horarios
- Conocimiento
- Canales
- Plan
- Avanzado

Los paneles actuales se reutilizan progresivamente, sin duplicar APIs.

El botón Guardar global deja de flotar fuera de contexto. Mientras no se implemente autosave, “Guardar cambios” solo aparece dentro de Configuración.

## 6. Operaciones

Operaciones deja de duplicar el workspace principal.

Mantiene funciones avanzadas:
- historial operativo;
- llamadas;
- eventos;
- métricas más profundas;
- diagnóstico técnico;
- certificación;
- proveedores y readiness en sección avanzada.

## 7. Conversaciones

Mantiene la bandeja unificada Voz + WhatsApp.

Requisitos:
- lenguaje humano para acciones;
- selección automática de la conversación más reciente;
- carga parcial por canal;
- soporte de deep links desde reservas/pedidos;
- detalle completo reutilizable desde Operaciones/Inicio.

## 8. Estados y errores

- Cada módulo carga de forma independiente.
- Un fallo de WhatsApp no debe ocultar llamadas.
- Un fallo de pedidos no debe ocultar reservas.
- Vacíos claros: “Todavía no hay…”.
- Nunca mostrar datos inventados.
- 401 conserva el flujo actual de autenticación.

## 9. Responsive

Desktop:
- tablas compactas;
- panel lateral de detalle.

Móvil:
- tarjetas;
- filtros compactos;
- detalle a pantalla completa.

## 10. Estrategia de implementación

Bloques cerrados:

1. Mover workspace operacional a Inicio y retirar duplicado de Operaciones.
2. Crear Configuración como pantalla propia y sacar configuración de Inicio.
3. Añadir filtros de Reservas.
4. Añadir relación Reserva ↔ conversación y detalle completo con transcripción/mensajes.
5. Extender detalle contextual a Pedidos.
6. Limpiar Operaciones avanzada y diagnóstico.
7. Revisión responsive y E2E final.

Cada bloque:
- cambio pequeño y coherente;
- prueba dirigida;
- commit;
- push;
- verificación mínima;
- una comprobación de Railway.

## Criterios de aceptación

- Inicio muestra las operaciones diarias sin obligar a entrar a Operaciones.
- Configuración no aparece debajo de Inicio.
- Reservas se pueden filtrar por fecha, servicio, estado, origen y búsqueda.
- Seleccionar una reserva muestra toda la trazabilidad disponible, incluida conversación/transcripción cuando exista.
- No se adivinan relaciones entre entidades.
- Pedidos y reservas usan tablas en desktop y tarjetas en móvil.
- Operaciones conserva diagnóstico e historial avanzado sin duplicar el workspace de Inicio.
- CI existente continúa verde.
