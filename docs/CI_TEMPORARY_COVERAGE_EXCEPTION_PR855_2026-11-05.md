# Retirada de la excepción temporal de cobertura diferencial Java para PR #855

**Estado:** RETIRADA. El umbral temporal del 80 % ya no está activo.  
**Retirada:** 2026-11-05, después de conseguir 100 % en líneas, ramas y métodos del delta de PR #855.  
**Rama:** `feature/aurora-wa-business-coexistence-guard-20261104`  
**PR de implementación:** [#855](https://github.com/Nicricht/helvoca/pull/855), DRAFT.  
**PR de validación CI:** [#856](https://github.com/Nicricht/helvoca/pull/856), DRAFT, mismo HEAD y dirigido a `main`.

## Alcance del control

PR #855 está apilado sobre una rama distinta de `main`. El workflow de CI valida el HEAD integrado mediante el PR paraguas #856, que apunta a `main`.

Para medir únicamente el delta de PR #855 y no mezclar el trabajo previo de PR #853, el workflow conserva como base de comparación:

`847eb9863bceabbca523985573d85407d2011e7e`

**Esta base específica no es una excepción de cobertura.** Los umbrales `DIFF_LINE_COVERAGE`, `DIFF_BRANCH_COVERAGE` y `DIFF_METHOD_COVERAGE` ya no se sobrescriben. El verificador usa sus valores predeterminados de **100 %** para líneas, ramas y métodos. Todos los demás PR también mantienen el 100 %.

## Evidencia histórica de la excepción

La excepción fue aprobada temporalmente el 2026-11-05 para permitir continuar mientras se cubrían las últimas rutas. No se eliminaron pruebas ni se relajaron los controles de seguridad o aislamiento por tenant.

La ejecución anterior `42049963831` documentó el déficit que justificó temporalmente el umbral. Esa condición quedó resuelta en la ejecución exacta descrita a continuación.

## Certificación final y retirada

Fuente: [GitHub Actions #42070632979](https://github.com/Nicricht/helvoca/actions/runs/42070632979).  
HEAD certificado: `b581c5ddb88d80fdb6270c46da00404a3bc6919b`.  
Base de comparación del delta PR #855: `847eb9863bceabbca523985573d85407d2011e7e`.

- Backend: **2.602 pruebas, 0 fallos, 0 errores**.
- Líneas diferenciales: **305/305 = 100 %**.
- Ramas diferenciales: **180/180 = 100 %**.
- Métodos diferenciales: **53/53 = 100 %**.
- Browser E2E: **630/630 aprobadas**.
- Fast Gate, Golden Journey, System Integration E2E, Home Frontend Coverage, Inventory Frontend Coverage y compatibilidad runtime histórico: **aprobados**.

El log JaCoCo del mismo HEAD confirma que la rama pendiente de `MetaWhatsAppTenantWabaAuthorizationService.java` quedó cubierta. Por tanto, se retira la excepción y se vuelve a exigir 100 % sin modificar los valores predeterminados del verificador.

## Garantías y límites

- No se eliminan ni se deshabilitan pruebas.
- No se rebajan controles de seguridad ni de aislamiento entre negocios.
- No se modifican secretos, variables de Railway, webhooks ni configuración de Meta/Twilio.
- No se envían mensajes reales ni se ejecutan llamadas, compras o migraciones.
- `main` y producción permanecen intactos.
- PR #855 y PR #856 permanecen abiertos y en DRAFT; no se autoriza merge ni despliegue.
- Esta certificación acredita el código y las pruebas offline. No acredita todavía la aprobación externa de Meta, el webhook de producción ni la entrega real de mensajes WhatsApp.
