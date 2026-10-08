# Operaciones premium: auditoría y plan de ejecución en cinco partes

**Fecha:** 2026-10-08  
**Repositorio:** `Nicricht/helvoca`  
**Baseline auditado:** `main@e3eed5dd9a268c9467d559e0eb077d77f4daa03b`  
**Pantalla:** `/app/orders`  
**Avance:** Parte 1/5 cerrada; Partes 2/5 y 3/5 implementadas en rama; certificación Full Gate del HEAD final pendiente.  
**Estado de código funcional:** UX/UI de Operaciones modificada exclusivamente en esta rama durante Parte 2; sin cambios de backend, BD, proveedores ni producción.  
**Riesgo previsto:** MEDIUM para UX/UI y cambios de frontend, HIGH si afectan transición de estados, tenants, inventario, persistencia, migraciones o proveedores.  
**FRAME CHANGE:** NO. Preservar shell, navegación, paleta y geometría protegida.

## 1. Decisiones de producto

- Mantener **Operaciones** en el menú y **Pedidos** como flujo principal; no convertir Operaciones en un segundo Inicio.
- Mantener **Agenda** separada para citas y reservas.
- **Clientes y Conversaciones** son perspectivas contextuales/filtros de Pedidos, no destinos independientes en la navegación principal.
- Las **Solicitudes** requieren una sección compacta y ordenada dentro de Operaciones, sin mezclarse semánticamente con pedidos.
- Adaptar acciones disponibles a capacidades reales habilitadas por negocio, sin inventar un estado o API por rubro.
- Priorizar acceso rápido al trabajo, estados reales, accesibilidad, seguridad y densidad visual. Las animaciones comunican cambios de estado y respetan `prefers-reduced-motion`.
- Conservar los activos premium ya aprobados; el hero ilustrado no debe ocupar la primera pantalla antes de la bandeja de trabajo.

## 2. Inventario verificado en el código

| Capa | Ubicación / contrato existente | Qué se preserva |
| --- | --- | --- |
| Ruta y UI | `frontend/src/pages/Orders/OrdersPage.tsx` + `.module.css` | `/app/orders`, AppShell, adaptaciones móvil y animaciones |
| Estado | `frontend/src/features/orders/useOrdersWorkspace.ts` | React Query, roles, permisos, consultas y refresco posterior a cambio de estado |
| API de pedidos | `frontend/src/features/orders/api.ts` | `GET /api/v1/commercial/orders`, `PATCH .../{id}/status`, `PATCH .../{id}/preparation-status` |
| Contexto | mismo cliente API | `/api/v1/operation-events`, `/api/v1/messaging/conversations/{id}`, `/api/v1/calls/{id}` como consultas opcionales |
| Seguimiento | `frontend/src/features/operations/OperationsSupportPanel.tsx` | Solicitudes, auditoría, exportaciones y sus permisos |
| Backend | `CommercialOperationsAdminController` / `CommercialOperationsAdminService` | Endpoints, alcance por negocio/tenant y validación de transición |
| Persistencia | `BusinessOrder`, repositorios y proyección comercial | UUID interno, totales y estados backend-autoritativos |
| QA | `e2e/react-orders.spec.js` | 8 contratos base, que NO prueban todas las mejoras aún |
| Gobernanza visual | `docs/frontend/FRAME_CONTRACT.md` | Shell protegido, seis viewports canónicos |

**Estado real de producción verificado en Railway:** entorno `production`, servicios `helvoca-api` y `Postgres` online, desplegado `helvoca-api` en `e3eed5dd9a268c9467d559e0eb077d77f4daa03b`, sin errores recientes del servicio reportados por el panel. Esto **no** demuestra por sí mismo cada viaje de usuario ni una cobertura de pruebas al 100%.

**Trabajo concurrente:** PR #761 `chore(qa): enforce 100 percent cross-layer quality contract` está abierto en Draft; no cambiarlo, absorberlo ni declararlo mergeado. Rebasar/verificar contra `main` actualizado antes de cualquier fase posterior, y respetar el estándar vigente en el momento de certificar.

## 3. Hallazgos accionables (observados, no conjeturas)

1. **Jerarquía de atención:** hero `visualHero` está antes de tarjetas y bandeja; reduce el espacio disponible para el trabajo del día.
2. **Tiempo real:** el hero dice «FLUJO EN TIEMPO REAL», pero `useOrdersWorkspace` no habilita `refetchInterval`, usa `refetchOnWindowFocus: false` y solo actualiza pedidos explícitamente después de una mutación. No prometer live hasta implementar y verificar mecanismo de actualización.
3. **Folio ilegible:** tanto tabla como panel muestran el UUID completo del pedido (`#${order.id}`). Diseñar referencia legible que no genere colisiones ni sacrifique identificadores internos.
4. **Estados reales:** `BusinessOrder.Status` solo contiene `CONFIRMED`, `PREPARING`, `READY`, `DISPATCHED`, `COMPLETED`, `CANCELLED`. «Nuevo» no es un estado del backend, no inventarlo.
5. **Transiciones estrictas:** `CONFIRMED → PREPARING | CANCELLED`; `PREPARING → READY | CANCELLED`; `READY → DISPATCHED` si entrega / `COMPLETED` si retiro; `DISPATCHED → COMPLETED`. Mantener permisos diferenciados `ORDERS_MANAGE` y `ORDERS_PREPARE`, bloqueo de dobles clics y respuesta 409.
6. **Contexto ya disponible:** el panel de detalle tiene cliente, productos, totales, envío/retiro, historial, mensajes/resumen de llamada y resiliencia ante fallas del contexto opcional. No duplicar este dominio.
7. **Clientes no son CRM:** la vista agrupa pedidos recibidos, por teléfono/nombre/ID. No se debe presentar como directorio completo de clientes.
8. **Conversaciones no son inbox independiente:** esta perspectiva filtra pedidos con origen WHATSAPP/VOICE y muestra su contexto en el detalle. No insinuar bandeja de todas las conversaciones.
9. **Solicitudes/auditoría:** la segunda sección ya existe (`OperationsSupportPanel`); hay que ordenar mejor su prioridad visual, no eliminar capacidades de gestión/exportación.
10. **Lista limitada:** el servicio retorna los **100 pedidos más recientes** por negocio. No construir indicadores «históricos/totales del negocio» con ese subconjunto sin paginación o métricas autoritativas nuevas.

## 4. Plan de cinco partes y criterios de aceptación

### Parte 1 | Auditoría, alcance, baseline, checkpoints (HECHA)
- Confirmar GitHub, rama principal, Railway y PR QA concurrente.
- Revisar UI, API, backend, datos, permisos, pruebas y contrato visual.
- Registrar hallazgos, riesgos, límites y definición de listo.
- Crear rama y PR Draft separado para continuidad. Ningún código en producción cambia.

### Parte 2 | Rediseño UX de pedidos (IMPLEMENTADA EN RAMA, QA EN CURSO)
- Compactar/reubicar el hero para que el usuario vea estado + bandeja sin desplazamiento excesivo.
- Encabezado operativo claro, tarjetas con métricas honestas, búsqueda y filtros eficaces.
- Dar protagonismo a pedidos y mover Clientes/Conversaciones a perspectivas contextuales compactas.
- Mejorar folios, densidad, accesibilidad y responsive, sin modificar frame.
- Mantener detalle lateral, totales, permisos, historial, cargas, errores y motion reducido.
- Añadir E2E/regresiones visuales al comportamiento realmente alterado; evidenciar seis viewports canónicos.


**Cambios de Parte 2 presentes en la rama:**
- Hero de robot gigante retirado del área operativa, reemplazado por franja compacta ilustrada de actualización manual.
- Tarjetas Activos/Preparando/Listos ahora filtran pedidos; `ACTIVE` es solo filtro local, no un nuevo estado de backend.
- La UI explicita que lista como máximo 100 pedidos recientes y la hora de la última consulta; no afirma sincronización en tiempo real.
- Identificadores de pedidos abreviados solo visualmente con `title` completo; todo `id` usado en eventos, API, testids, permisos y acciones sigue siendo el original.
- Se conservaron conversación contextual, detalle lateral, precios backend-autoritativos, solicitudes/auditoría, manejo de 409 y protección de doble envío. Un fallo de actualización manual conserva los pedidos previamente obtenidos y muestra aviso.
- Regresión: `e2e/react-orders.spec.js` amplía de 8 a 12 pruebas para jerarquía, refresco bajo demanda, referencias completas, filtros de estado y preservación de pedidos ante fallo de actualización.
- Efecto en el frame: **FRAME CHANGE: NO**, solo `OrdersPage.tsx` y CSS local; el arte aprobado `hero-order-robot.webp` sigue presente en formato pequeño.
- E2E/CI del HEAD final y seis viewports: pendientes de verificación. No declarar la fase certificada mientras estén pendientes.

### Parte 3 | Flujo de gestión robusto y adaptación empresarial (IMPLEMENTADA EN RAMA, QA EN CURSO)
- Validar con la matriz de capacidades existentes las vistas relevantes por rubro (pedido, cotización, solicitudes, delivery), sin duplicar Agenda.
- Auditar frontend ↔ controlador ↔ servicio ↔ PostgreSQL de acciones y transiciones.
- Cualquier nuevo folio real, paginación, indicador agregado o estado exige contrato backend, posible migración y pruebas correspondientes. No fingirlo en UI.
- Mantener separaciones tenant y permisos; probar transiciones inválidas, intentos de otro tenant y doble envío donde corresponda.

**Cambios de Parte 3 presentes en la rama:**
- Vista contextual **Cotizaciones** en Operaciones, solo para usuarios con `QUOTES_READ` y sin separarla en un módulo principal de navegación.
- Integración con `GET /api/v1/commercial/quotes` y `PATCH /api/v1/commercial/quotes/{id}/status` existentes, sin enviar `businessId` desde el navegador. Estado y total provienen del backend.
- Manejo de transiciones legales REQUESTED → READY/CANCELLED; READY → ACCEPTED/REJECTED/CANCELLED; los estados terminales no muestran acciones.
- El frontend solo muestra controles de mutación con `QUOTES_MANAGE`; el backend conserva `@PreAuthorize('PERM_QUOTES_MANAGE')` y filtrado por tenant.
- Los usuarios con `QUOTES_READ` sin `ORDERS_READ` ven cotizaciones sin consultar pedidos; quienes no tienen ambos accesos reciben pantalla de autorización, sin consulta de pedidos.
- Permisos de lectura de pedidos ahora habilitan/deshabilitan el query React Query según `auth/me`, evitando llamadas evitables sin permiso.
- Nuevo panel soporta estados loading, empty, read-only, 409/conflicto, actualización autoritativa y advertencia si refetch falla.
- `e2e/react-orders.spec.js` expandido a **17 pruebas** (8 originales + 9 nuevas), incluidos flujos quote-only/readonly/denied, cambios de estado, error y control de llamadas `businessId`.
- `CommercialOperationsAdminServiceTest`, `CommercialOperationsReadRepositoryIntegrationTest` y `PostgresRowLevelSecurityIntegrationTest` ya cubren familias relevantes de transiciones / alcance por negocio / aislamiento en PostgreSQL; su existencia no significa que el HEAD nuevo esté aprobado, se exige evidencia verde en CI.
- No se modificaron migraciones, repositorios Java, proveedor WhatsApp/voz ni producción.
- La pestaña de cotizaciones se controla por **permisos de usuario**, no por un switch automático de industria; la disponibilidad comercial por rubro deberá reflejar también los presets/capacidades de negocio sin suposiciones, si esto se amplía en el futuro.
- La cotización NO se convierte automáticamente en pedido, y marcarla aceptada no implica cobro, entrega ni orden creada.

### Parte 4 | Actualización verificable e IA explicable (PENDIENTE)
- Implementar actualización de cambios mediante mecanismo fiable (polling acotado/invalidación o push real según soporte comprobado), evitando consumo excesivo.
- Mostrar origen, tiempo de última sincronización y estados de conexión basados en hechos.
- Mostrar actividad de IA **solo cuando** existen eventos reales y verificables de operación. Nunca simular acciones, mensajes, pagos o llamadas.
- Resolver fallos parciales de contexto sin bloquear pedidos y sin efectos externos no autorizados.

### Parte 5 | Certificación, integración y entrega (PENDIENTE)
- Fast Gate, suite dirigida, suite completa requerida, E2E, Golden Journey y evidencia exact-HEAD.
- Verificar responsive en 1536×950, 1440×900, 1366×768, 1280×720, 768×1024, 390×844.
- Comprobar permisos, estado persistente, fallas de API, transición 409, dobles clics, multi-tenant e idempotencia según impacto real.
- Resolver dependencia con PR #761; actualizarse sobre `main` antes del merge.
- Solo cuando CI exact-HEAD sea verde, convertir Draft, merge protegido, certificar CI exact-main y Railway exact-SHA, revisar salud y limpiar rama.

## 5. Condiciones para avanzar sin regresiones

- **La fuente de verdad es Spring Boot/PostgreSQL**, no el estado optimista del navegador.
- Prohibido crear pedidos, llamar, enviar WhatsApp, cobrar o activar providers reales para probar apariencia visual.
- El contexto opcional fallido no oculta ni invalida el pedido.
- Si el usuario no tiene permisos, nunca se habilitan acciones solo por estética.
- Nada de KPIs falsos, estado «En vivo» sin mecanismo real o IDs visibles no inequívocos.
- El usuario no es el sistema de QA: las validaciones las lleva la implementación.
- Los resultados de CI anteriores no certifican una nueva revisión.
- Los cambios se realizan exclusivamente en esta rama hasta su certificación.

## Checkpoint para continuar

**Parte 3 implementada, QA en curso.** Continuar con Parte 4 solo cuando el usuario escriba «continua». Antes de avanzar, comprobar CI exact-HEAD de este PR, corregir lo que falle (también potenciales regresiones de Parte 2) y refrescar `main` y el PR concurrente #761. Ninguna garantía de producción hasta los gates y Railway exact-SHA de Parte 5.
