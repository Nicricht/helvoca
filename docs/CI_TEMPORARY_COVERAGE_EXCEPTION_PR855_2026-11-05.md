# Excepción temporal de cobertura diferencial Java para PR #855

**Estado:** activa y temporal para completar la etapa de coexistencia WhatsApp.  
**Aprobación:** 2026-11-05, por el propietario del repositorio.  
**Rama:** `feature/aurora-wa-business-coexistence-guard-20261104`  
**PR de implementación:** [#855](https://github.com/Nicricht/helvoca/pull/855), DRAFT.  
**PR de validación CI:** [#856](https://github.com/Nicricht/helvoca/pull/856), DRAFT, mismo HEAD y dirigido a `main`.

## Alcance exacto

PR #855 está apilado sobre una rama distinta de `main`. El workflow de CI escucha pull requests dirigidos a `main`, por lo que GitHub ejecuta la validación del mismo HEAD mediante el PR paraguas #856.

Para que la excepción no cubra también el cambio anterior apilado en PR #853, el paso JaCoCo del PR #856 compara únicamente el delta de PR #855 desde su base:

`847eb9863bceabbca523985573d85407d2011e7e`

Solo cuando coinciden PR #856, base `main` y la rama exacta de coexistencia, el workflow usa esa base y exige como mínimo:

- Líneas diferenciales: **80 %**.
- Ramas diferenciales: **80 %**.
- Métodos diferenciales: **80 %**.

Para cualquier otro PR se conserva su propia base y el umbral de **100 %**. Los valores predeterminados de `scripts/ci/check_diff_coverage.py` siguen siendo 100 %. No se modifican los gates de seguridad, aislamiento por tenant, Golden Journey, integración, frontend ni runtime.

## Evidencia de cobertura

Fuente: [CI #42049963831](https://github.com/Nicricht/helvoca/actions/runs/42049963831), HEAD evaluado `4d270ae869241d551c1a3bd9fe09ceb435180282`, artefacto `backend-test-diagnostics` (JaCoCo XML y reportes Surefire).

- Pruebas backend: **2.598 ejecutadas, 0 fallos y 0 errores**.
- El resultado del workflow completo apilado contra `main` fue 672/673 líneas (99,9 %), 416/422 ramas (98,6 %) y 158/158 métodos (100 %).
- Recalculado el delta específico de PR #855 contra su base `847eb9863bceabbca523985573d85407d2011e7e`: **388/389 líneas (99,7 %), 215/224 ramas (96,0 %) y 55/55 métodos (100 %)**.
- En el delta de PR #855 queda una línea ejecutable sin cubrir en `MetaWhatsAppEmbeddedSignupAuthorizationCodeService.java:145` y nueve resultados de rama pendientes en ocho ubicaciones.

El fallo original del gate fue exclusivamente por exigir 100 % en todos los indicadores. Las pruebas funcionales del backend no registraron fallos ni errores.

## Garantías

- No se eliminan ni se deshabilitan pruebas.
- No se rebajan controles de seguridad ni de aislamiento entre negocios.
- No se cambia lógica de producción, secretos, variables de Railway, webhooks ni configuración de Meta/Twilio.
- No se envían mensajes reales ni se ejecutan llamadas, compras o migraciones.
- `main` y producción permanecen intactos.
- PR #855 y PR #856 permanecen abiertos y en DRAFT; no se autoriza merge ni despliegue.

## Retirada obligatoria

Al completar esta etapa de coexistencia, retirar la condición temporal del workflow, restaurar el 100 % para líneas, ramas y métodos, volver a usar la base del PR de validación y ejecutar nuevamente el Full Gate. Esta excepción no debe convertirse en la política permanente de RecepVoz.
