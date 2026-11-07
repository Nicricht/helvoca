# Auditoría de activación por rubro · RecepVoz · 2026-11-06

**Estado:** mejora UI propuesta en [PR #860](https://github.com/Nicricht/helvoca/pull/860) (DRAFT, no desplegada); la meta de `≤15 min` **no está medida ni certificada**. No sustituye [la ruta única #823](https://github.com/Nicricht/helvoca/issues/823), [PR #858](https://github.com/Nicricht/helvoca/pull/858) ni [la importación #776](https://github.com/Nicricht/helvoca/issues/776).

## Qué existe hoy y NO se reimplementa

| Capacidad | Evidencia de código | Decisión |
| --- | --- | --- |
| Progreso inicial Negocio → Servicios → Horarios → Recepcionista | `frontend/src/pages/Home/HomePage.tsx`, `getOnboardingStatus` | Mantener los cuatro pasos y su respuesta autorizada, sin inventar un asistente paralelo |
| Configuración exprés Importar → Revisar → Preparar | `frontend/src/pages/Settings/SettingsExpressIntro.tsx` | Mantener acción principal y revisión humana |
| Ocho pestañas avanzadas y edición independiente | `SettingsPage.tsx`, `SettingsPanelView.tsx`, `sectionSave.ts` | Mantener guardados aislados y permisos BUSINESS_ADMIN |
| Rubro del negocio `presetKey` | `BusinessProfileService.normalizePreset`, `SettingsPanelView.tsx` | Reutilizar perfil existente; no agregar tabla/endpoint |
| Importación asistida de planillas, fotos y documentos | Issue #776, PR #777 y auditoría previa | No duplicar; importación IA pagada permanece controlada y con aprobación de datos |
| Preparación de canales | Estado onboarding + guía de requisitos | El progreso no demuestra llamadas/WhatsApp reales; no activar canales desde un selector |

## Brecha concreta comprobada en esta auditoría

Antes del PR #860, el selector de rubro ofrecía comercio, ferretería, servicios, restaurante, clínica, salón, taller, hotelería y profesional, pero **no veterinaria**; la lista de recomendaciones cubría solo algunos rubros con textos muy generales.

**Delta del PR #860:** añadir el valor compatible `veterinary` y guías de datos a revisar en diez rubros. La selección y su consejo son puramente informativos; no prellenan servicios, precios, inventario, horarios, profesionales, diagnóstico, reservas ni canales. Solo `Guardar cambios` solicita `PUT /api/v1/business/profile` con el valor elegido. Se conserva el comportamiento del caso de ferretería ya cubierto por `e2e/settings-full-cutover.spec.js`.

Esto es un **primer incremento**, no la implementación ni la validación de un onboarding completo en quince minutos.

## Material para configurar correctamente según el negocio

Estos datos son una **lista de verificación del titular**, no plantillas con precios, disponibilidad u horarios asumidos.

| Rubro | Datos que debe validar el propietario | Precaución de la IA |
| --- | --- | --- |
| Restaurante / pizzería | Carta, variantes, precios, productos agotados, horarios, retiro, despacho, reservas y preguntas frecuentes | No inventar stock, menú, cobertura ni tiempos de entrega |
| Veterinaria | Especies atendidas, servicios, profesionales, horarios, urgencias, precios, reservas e instrucciones de contacto | No diagnosticar ni dar recomendaciones veterinarias no verificadas; urgencias según protocolo autorizado |
| Ferretería | Productos, tamaños, unidades, stock, cotizaciones, retiro, despacho, políticas | No confirmar disponibilidad sin inventario o fuente verificada |
| Clínica / centro médico | Especialidades, profesionales, duración, horarios, precios, modalidades de reserva y privacidad | No inventar citas ni dar indicaciones clínicas; cuidado con datos sensibles |
| Tienda y comercio | Catálogo, variantes, precios, disponibilidad, devoluciones, entregas | No asegurar disponibilidad o políticas no suministradas |
| Servicios y profesionales | Servicios, duración, tarifas, ubicación o zona de cobertura, horarios, disponibilidad | No prometer un servicio o reserva que no esté habilitado |
| Belleza / salón | Servicios, profesionales, duraciones, precios y citas | No confirmar disponibilidad sin consultar agenda |
| Taller | Reparaciones, presupuestos, tiempos, requisitos, horarios y contacto | No garantizar un plazo sin autorización |
| Alojamiento | Habitaciones, tarifas, check-in/check-out, políticas, disponibilidad | No afirmar disponibilidad sin sistema verificado |

## Criterios E2E de este PR

**Prueba automatizada nueva:** `e2e/react-settings-independent-save.spec.js`.

1. Cada uno de los diez rubros presenta consejo correspondiente visible después de `selectOption`, sin escribir datos ni realizar llamadas externas.
2. El propietario conserva sus campos existentes y no genera reservas, inventario, servicios o canales de forma implícita.
3. Al elegir veterinaria, `Guardar cambios` envía exclusivamente `PUT /api/v1/business/profile`, mantiene banderas existentes de servicios/reservas y permite recarga con el mismo rubro.
4. Cambiar a `General` oculta el consejo, no borra catálogos ni horario y solo persiste el `presetKey` vacío después de pulsar Guardar.
5. El rol OPERATOR ve información y consejo persistidos, pero no puede cambiar rubro ni guardar.
6. Prueba existente de ferretería conserva el contrato original; el consejo adicional sigue siendo solo informativo.

**Pruebas existentes que no se duplican:** `e2e/react-settings-express.spec.js` cubre entrada exprés, modo operador, importación no pagada/denegación y seis resoluciones; `e2e/react-settings-independent-save.spec.js` cubre guardado por sección y rechazos. El importador de Issue #776 tiene su propia ruta de aprobación. La fuente actual no demuestra todavía un tiempo real de configuración.

## Meta de producto: intervención inicial de 15 minutos

**Hipótesis a medir, no promesa comercial:** un administrador de cada sector representativo (pizzería, veterinaria, ferretería y clínica), con catálogo y horarios disponibles, termina la **configuración básica de negocio + recepcionista con revisión humana** en 15 minutos de intervención, sin incluir aprobaciones externas de Meta/Twilio ni pruebas reales que requieren tercero.

Propuesta de validación con usuarios autorizados, sin llamadas pagadas ni clientes reales por defecto:

- Medir desde el primer acceso hasta perfil, servicios/catálogo, horarios, FAQ y saludo revisados y guardados; registrar tiempo total, clics, errores, campos corregidos y bloqueos.
- Para un piloto de UX, comenzar con al menos **cinco sesiones representativas por rubro** y reportar mediana, percentiles y distribución, sin ocultar fallos ni asegurar estadística poblacional.
- Objetivo provisional: **mediana ≤15 minutos** de trabajo efectivo por rubro; no declarar alcanzado sin datos. Para documentos que no contienen información suficiente, identificar las preguntas faltantes en vez de inventar.
- Control obligatorio: después de configurar no se ha probado que exista canal real. La interfaz debe separar **base configurada** de **voz/WhatsApp certificado**.

## Brechas posteriores que NO bloquean el primer piloto

1. Recorrido de onboarding guiado desde la primera pantalla con detección de campos realmente faltantes y un solo CTA de siguiente paso, sin saltar al canal antes de estar preparado.
2. Auditoría de importación revisable de Issue #776 para cuatro rubros, con origen/confianza por campo y detección de conflictos, sin gastar IA por defecto.
3. Medición real de tiempo y errores; accesibilidad móvil y reanudación segura de progreso tras sesión interrumpida.
4. Contexto omnicanal y CRM ligero solo **después** de validar consentimiento, identidad y primer piloto, reutilizando las bases existentes.

## Seguridad, coste y entrega

- Riesgo de este PR: **LOW**, cambio de etiquetas y consejo + Playwright; no cambios Java, SQL, RLS, esquema, tokens, API, proveedores, pagos, reservas ni datos de negocios.
- Calidad requerida antes de marcar DONE: CI exact-HEAD terminal SUCCESS, suites aplicables y 100 % de comportamientos afectados; no confundir cobertura diferencial con cobertura global del repositorio.
- Mientras PR #860 esté DRAFT no existe cambio en `main` ni despliegue. Tampoco está aprobada la operación real de WhatsApp de Aurora.
- **Topología temporal:** PR #860 nace de la rama documental de PR #858 para habilitar el recibo pre-cambio exigido por el repositorio antes del primer commit. El PR debe continuar DRAFT, con revisión de su base/diff antes de cualquier decisión de merge.

**Siguiente acción segura tras CI:** revisar el PR #860 y, solo cuando se haya validado la experiencia por rubros, considerar un segundo incremento centrado en el primer CTA guiado y en medición del tiempo. No crear otro onboarding o CRM integral por anticipado.

## Incremento 2 · recorrido guiado de un clic (PR #860, 2026-11-06)

**Problema comprobado:** aunque Settings Express ya enseñaba Importar → Revisar → Preparar y mantenía las ocho pestañas, no había un botón que dirigiera al siguiente requisito devuelto por el backend. El cliente debía localizar por su cuenta la sección adecuada.

**Cambio exclusivo de frontend (sin API ni migración nuevas):**
- `SettingsExpressIntro.tsx` interpreta **únicamente** los siete valores reconocidos de `/api/v1/onboarding/status.nextStep`: `CONFIGURE_BUSINESS` → Negocio; `ADD_SERVICE` → Servicios; `CONFIGURE_HOURS` → Horarios; `CONNECT_PHONE_NUMBER` → Canales; `OPTIONAL_HUMAN_TRANSFER` → Negocio; `OPTIONAL_KNOWLEDGE` → Conocimiento; `READY` → Recepcionista IA.
- El botón **Ir a ...** cambia la pestaña existente en `SettingsPage` y la URL `?section=...`, desplazando hasta la zona de edición; no guarda nada ni llama a ningún proveedor. El botón aparece solo para `BUSINESS_ADMIN`; `OPERATOR` ve una indicación de solo lectura.
- Si la API está indisponible, carece de `nextStep` o devuelve código desconocido, la interfaz muestra **No hay un siguiente paso verificado** y no inventa una acción.
- `READY` significa únicamente preparación básica según backend: el texto aclara que no certifica llamadas ni WhatsApp reales. `CONNECT_PHONE_NUMBER` tampoco activa el teléfono.
- La tarjeta es adaptable al móvil, admite teclado y respeta la preferencia del sistema de movimiento reducido.

**Pruebas añadidas** a `e2e/react-settings-express.spec.js`: los siete códigos reconocidos, destino/selección/URL, cero escrituras al navegar, teclado Enter, código desconocido y ausencia de valor, rol OPERATOR sin acción. Se conservan los tests de importación, seis resoluciones y acciones de guardado aislado existentes.

**Objetivo de rapidez, pendiente de evidencia:** planteamos como *stretch* **≤10 minutos de intervención de configuración básica** para un cliente que ya dispone de sus datos, manteniendo el criterio de referencia **≤15 minutos**. Ningún tiempo está medido aún; aprobaciones externas, activación y certificación real de canales quedan fuera de la medición del asistente básico. Se debe medir con sesiones consentidas de pizzería, veterinaria, ferretería y clínica y registrar mediana, fallos y pasos recorridos. No usar la mejora visual como prueba de ahorro de tiempo.

**Salida:** `IMPLEMENTED_IN_DRAFT`, `CI_EXACT_HEAD_PENDING` hasta comprobar el run final; no merge ni despliegue. Siguiente acción: certificación Fast/Full Gate y Playwright exactos sobre la última revisión del PR #860.
