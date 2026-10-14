# Regla obligatoria: documentar cada trabajo terminado y evitar repeticiones

**Vigencia:** 2026-10-14 · **Autoridad:** [AGENTS.md](../../AGENTS.md) · **Tablero único V1:** [Issue #823](https://github.com/Nicricht/helvoca/issues/823) (C0–C7).

## Objetivo

Cada funcionalidad, corrección, prueba, integración, auditoría u operación completada debe quedar suficientemente documentada **en GitHub** para que otra sesión pueda continuar sin repetir decisiones, código, diagnósticos, pruebas ni acciones externas. **Trabajo sin documentación verificable = trabajo no cerrado.**

La documentación es una **parte del entregable**, no una tarea opcional posterior. El agente la escribe y actualiza; el usuario no debe recordar en qué archivo quedó algo ni reconstruir conversaciones previas.

## Dónde registrar la información (sin crear planes paralelos)

1. **Código/contrato/procedimiento:** actualizar, dentro del mismo PR, la documentación técnica o runbook que corresponda, solo cuando exista un cambio significativo de comportamiento, configuración, arquitectura o procedimiento. Reutilizar la página existente antes de crear una.
2. **Estado de trabajo y reanudación:** mantener el checkpoint **en el mismo PR/issue**. Antes de iniciar trabajo ya conocido, consultar ese registro, la rama HEAD y los CI asociados.
3. **Progreso comercial V1:** reflejar el resultado en el [único Issue maestro #823](https://github.com/Nicricht/helvoca/issues/823), etiquetado conceptualmente C0–C7, y mantener coherencia con `docs/PLAN_MAESTRO_CIERRE_COMERCIAL.md` de su rama de integración. El Issue #823 es el tablero, no crear otro.
4. **Trabajo fuera de V1:** usar su issue y PR existentes. No introducir artificialmente una etapa Cn.

En un cambio mínimo de riesgo LOW puede bastar la descripción/registro breve en el PR y un enlace al archivo modificado. No crear un Markdown nuevo para cada línea cambiada.

## Regla operativa transversal: comprobar recursos existentes antes de pedir intervención

**Primero comprobar, después pedir.** Esta sección aplica a **toda intervención futura de ChatGPT y otros agentes** en Helvoca, no solo al cierre de una tarea. La instrucción obligatoria está en [`AGENTS.md`](../../AGENTS.md) y este documento la desarrolla.

- **Antes de pedir** al titular crear o editar una cuenta, secreto, servicio, variable, repositorio, backup, plan de pago, permiso o prueba, buscar primero evidencias en el repositorio, Issue #823, PRs, documentación, CI y conectores ya disponibles. Comprobar existencia/estado, no volver a preguntar por algo que ya se sabe. Un secreto existente **no prueba por sí mismo** que su valor actual sea correcto; el conector puede leer metadatos sin tener acceso a secretos. Distinguir `EXISTE`, `VALIDADO`, `NO VERIFICADO` y `AUSENTE`.
- **Usar primero la vía segura propia**: inspección de solo lectura, herramientas autorizadas y pruebas aisladas sin credenciales visibles. No pedir al usuario ejecutar comandos, copiar claves o reenviar capturas que ya pueden comprobarse por herramientas. No exponer valores, respuestas confidenciales ni registrar datos personales.
- **Responsabilidad técnica del agente**: implementar, corregir, testar, comprobar CI exacto y documentar sin convertir al usuario en operador de QA. No repetir tareas ya certificadas salvo cambio causal demostrado.
- **Escalar una sola acción concreta** únicamente cuando una limitación de permisos, cuenta personal, autorización de coste o restricción real impida avanzar; explicar brevemente qué se comprobó, cuál es el bloqueo y por qué es indispensable. Si el usuario ya autorizó acciones ordinarias dentro de su alcance, no re-preguntar sin motivo.
- **Errores de coordinación**: si antes se pidió crear algo existente, anular la indicación innecesaria, continuar con el recurso disponible y dejar un checkpoint que evite repetir el error.

### Caso que motiva la regla: C5 Mercado Pago (2026-10-14)

El repositorio ya contenía el secret de Actions `MERCADOPAGO_TEST_ACCESS_TOKEN`; se pidió al titular crearlo de nuevo sin revisar el estado. Luego se verificó con consulta GET de solo lectura y sin exponer secretos que la identidad autenticada tenía el **mismo User ID del vendedor TEST** previamente evidenciado; el nombre de acceso del panel y el nickname de API diferían. No se necesitó crear ni cambiar el secreto. Registro seguro y evidencia de regresión: [PR #834](https://github.com/Nicricht/helvoca/pull/834), [CI #38536658024](https://github.com/Nicricht/helvoca/actions/runs/38536658024) (GET autenticado y 16 tests) y [CI #38536658035](https://github.com/Nicricht/helvoca/actions/runs/38536658035) (23 tests Python, 15 Java PASS y test externo omitido). **No** indica checkout, webhook ni cobro realmente certificados; C5 sigue pendiente de la transacción TEST aislada.

**Prohibición persistente:** no volver a solicitar la creación/reemplazo de ese secreto ni nuevas cuentas TEST salvo evidencia nueva de revocación, cambio o ausencia. No fusionar a `main` ni intervenir producción en nombre de esta regla. Toda nueva sesión debe continuar desde los checkpoints vigentes y realizar sus propias comprobaciones proporcionales.

## Registro de finalización obligatorio

Antes de decir «terminado», incluir o actualizar un bloque equivalente al siguiente. No inventar valores desconocidos, ni escribir `PASS` para una prueba pendiente:

```markdown
### Registro de finalización / checkpoint · AAAA-MM-DD
- Etapa/objetivo: Cn (si aplica) y criterio de aceptación concreto.
- Estado: DONE_VERIFIED | DONE_OFFLINE_ONLY | IN_PROGRESS | BLOCKED | NO-GO.
- Qué se hizo: resumen preciso, rutas/capacidades modificadas, razón de los cambios.
- Qué NO se hizo: operaciones en producción, proveedor, dinero, llamadas, migraciones, etc.
- Git: repositorio, rama, PR, commit HEAD exacto y relación con main.
- Pruebas: comandos o CI URL, SHA verificado, resultados/número de casos, alcance de la prueba.
- Seguridad/datos: aislamiento de tenants, integridad, secretos, riesgos y rollback según aplique.
- Decisiones: acuerdos ya tomados, descartes o excepciones y por qué.
- Pendiente/bloqueos: requisito sin evidencia; persona/permiso externo necesario si aplica.
- Próximo paso: una acción específica, quién puede ejecutarla y condición de inicio.
- Documentos existentes relacionados: enlaces canónicos, sin copiar todo su contenido.
```

**Estado NO es evidencia:** un commit subido, un CI `queued`, una prueba sintética o una intención no certifican producción ni un cobro/llamada/restauración reales. `DONE_OFFLINE_ONLY` deja pendientes explícitos. Un mensaje de «hecho» nunca sustituye un enlace de GitHub o proveedor.

## Cuándo documentar

- **Antes de comenzar:** leer AGENTS, este protocolo, el plan/issue y el checkpoint del PR pertinente. Identificar lo ya hecho y su evidencia exacta para no reabrirlo.
- **Durante trabajo prolongado o al encontrarse un bloqueo:** actualizar el checkpoint antes de parar, con el estado verdadero, lo que falló y el siguiente paso decisivo. No presentar un fallo como terminación.
- **Al cerrar cada bloque relevante:** en el mismo ciclo del código y pruebas, actualizar documentación + checkpoint + tablero Cn si aplica, con URLs y SHA del resultado final.
- **Al cambiar código después del último CI:** la evidencia anterior queda obsoleta para lo modificado. Repetir solo la validación afectada y las puertas finales obligatorias; registrar el nuevo SHA y ejecución.
- **Al hacer merge o despliegue autorizado:** cerrar el historial con SHA de `main`, CI de ese SHA, identificador real del despliegue, migraciones, health/rollback y riesgos. No inferir producción de un PR aprobado.
- **Al cambiar de chat o retomar:** reconstruir la secuencia `Issue #823 → Cn → PR → rama → HEAD → CI → estado → próximo paso`. Si todavía hay evidencia válida, reutilizarla.

## Regla anti-repetición

**No se vuelve a ejecutar una acción ya certificada sobre el mismo artefacto y contexto**, salvo que:
- el código/configuración/proveedor/entorno relevante haya cambiado;
- el resultado haya caducado;
- exista un fallo nuevo reproducible o una brecha de evidencia real;
- se requiera un nuevo gate obligatorio sobre un HEAD diferente.

Antes de repetir un test caro, exportar datos, consumir APIs de pago, contratar infraestructura o solicitar capturas/comandos al usuario, explicar la **brecha nueva concreta** y por qué las evidencias anteriores no sirven. No repetir nunca efectos externos reales por conveniencia del test.

## Seguridad y autoridad

- Nunca guardar secretos, OAuth tokens, refresh tokens, contraseñas, claves privadas, dumps de clientes, datos personales, transcripciones ni informes privados en GitHub o respuestas públicas.
- Para evidencia privada usar solo identificador seguro/redactado, ubicación restringida o atestación del responsable, aclarando qué se ha verificado y qué no.
- Ninguna anotación, checkpoint o automatización concede permiso para hacer merge a `main`, desplegar, migrar producción, cobrar, comprar, llamar ni cambiar variables sensibles.
- Los gates de C1 (restauración antes de producción) y de C2–C7 siguen intactos; documentar un trabajo offline no lo convierte en aprobación comercial.

## Comprobación final antes de responder al usuario

- [ ] Estado y aceptación coinciden con los hechos realmente verificados.
- [ ] Commit final, PR y enlaces CI correctos; pruebas todavía válidas para ese HEAD.
- [ ] Qué se completó y qué queda pendiente se distinguen claramente.
- [ ] La documentación afectada y el mismo checkpoint de PR están actualizados.
- [ ] Para Helvoca V1, el Issue #823 apunta al avance de la puerta Cn.
- [ ] El siguiente agente puede continuar directamente sin preguntar de nuevo ni repetir.
- [ ] No se expuso información sensible ni se ejecutaron efectos externos no autorizados.

**Si alguna casilla esencial falla, declarar `IN_PROGRESS` o `BLOCKED` y registrar el siguiente paso, no «DONE».**
