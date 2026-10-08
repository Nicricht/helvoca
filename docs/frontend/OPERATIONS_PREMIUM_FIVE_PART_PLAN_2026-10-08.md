# Operaciones premium: auditoría y plan de ejecución en cinco partes

**Fecha:** 2026-10-08  
**Repositorio:** `Nicricht/helvoca`  
**Baseline auditado:** `main@e3eed5dd9a268c9467d559e0eb077d77f4daa03b`  
**Pantalla:** `/app/orders`  
**Avance:** Partes 1/5, 2/5, 3/5 y 4/5 implementadas en rama; Full Gate/CI del HEAD final aún pendiente. Parte 5 reservada para certificación, merge, Railway exact-SHA y cleanup.  
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

### Parte 4 | Actualización verificable e IA explicable (IMPLEMENTADA EN RAMA, QA EN CURSO)
- Implementar actualización de cambios mediante mecanismo fiable (polling acotado/invalidación o push real según soporte comprobado), evitando consumo excesivo.
- Mostrar origen, tiempo de última sincronización y estados de conexión basados en hechos.
- Mostrar actividad de IA **solo cuando** existen eventos reales y verificables de operación. Nunca simular acciones, mensajes, pagos o llamadas.
- Resolver fallos parciales de contexto sin bloquear pedidos y sin efectos externos no autorizados.

**Cambios de Parte 4 presentes en la rama:**
- React Query `["commercial", "orders"]` habilita `refetchInterval: 60_000` con `refetchIntervalInBackground: false`, refresco al recuperar foco/conexión. La consulta sigue condicionada a `auth/me` y `ORDERS_READ`.
- La lista contextual de cotizaciones tiene el mismo intervalo, únicamente mientras la vista `OperationsQuotesPanel` está montada y el rol tiene `QUOTES_READ`; las otras pestañas no solicitan cotizaciones.
- El encabezado Operaciones explica explícitamente **sincronización periódica**, nunca promete WebSockets, push inmediato ni IA en ejecución. Se muestra la hora **correcta** de la última consulta y «Sin conexión» con datos previamente cargados.
- Ante error de refetch la pantalla conserva el resultado previamente obtenido, comunica que los datos pueden estar desactualizados y permite reintento manual.
- El nuevo panel «Actividad de IA registrada» aparece **solo** si `/api/v1/operation-events?operationId=...` retorna `actorType: "AI"`; muestra `eventType`, canal y fecha sin inventar efectos terminales, cobros, mensajes ni llamadas. Hasta tres eventos ordenados cronológicamente, sin mostrar payloads sensibles.
- El endpoint de historial mantiene su autorización backend existente `hasAnyRole('BUSINESS_ADMIN','OPERATOR')`. Para otros roles, una respuesta 403 se trata como falla de contexto opcional; no se relaja autorización ni se oculta el pedido.
- La carga de historial se vuelve a intentar cuando cambia el `updatedAt` autoritativo del pedido, por ejemplo tras una mutación exitosa y refresco de lista.
- 5 nuevos casos E2E: sondeo de pedidos y cotizaciones con reloj controlado, evento IA real, ausencia de IA cuando hay solo actividad humana o error, y aviso sin conexión. Total de casos en `e2e/react-orders.spec.js`: **22**.
- Sin nuevos endpoints, migraciones, pagos, proveedores activos ni cambios de permisos en backend. `FRAME CHANGE: NO`.
- **Restricción consciente:** un sondeo cada 60 s ofrece *sincronización periódica*, no eventos instantáneos. Eventual push (WebSocket/SSE) requiere diseño y certificación de infraestructura futuros. No llamar «tiempo real estricto» a esta implementación.

**Riesgos a certificar:** intervalos suspendidos en segundo plano; ausencia de consulta sin permiso; contexto 403 con fallo parcial; revalidación de estados concurrentes; sin filtración cross-tenant; quote-only sin consultas de pedidos; degradación segura con servidor caído.

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

**Parte 4 implementada, QA en curso.** Próximo paso condicionado al «continua» del usuario: Parte 5, certificación final exact-HEAD en GitHub Actions, corrección de cualquier prueba roja, verificación de responsive, merge solo con CI green y sin conflictos, exact-main CI, despliegue Railway exact-SHA y cleanup.

- Rama: `feat/operations-premium-workspace-20261008`
- PR: #762 (Draft; **no** mergeado)
- Último commit de implementación funcional de Parte 4: `415ced2b23af577c3a0378478c36af1b0ea45d61` (22 pruebas Playwright definidas; su ejecución no se presume aprobada).
- Base revisada: `main@e3eed5dd9a268c9467d559e0eb077d77f4daa03b`
- PR #761 continúa siendo trabajo QA independiente y deberá comprobarse antes del merge.
- Todas las credenciales, integraciones de Twilio/WhatsApp, pagos, telefonía y datos reales permanecen intactos.
- Cualquier comprobación de CI de un SHA anterior queda invalidada por nuevos commits.

