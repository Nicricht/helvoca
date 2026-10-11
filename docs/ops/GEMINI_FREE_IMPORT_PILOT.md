# RecepVoz: piloto gratuito y aislado de Gemini

**Estado: preparado, sin llamadas a Gemini ni claves copiadas.** La prueba es exclusivamente con archivos sintéticos y en un proyecto personal propio.

## En Google AI Studio

1. Ir a https://aistudio.google.com/projects y seleccionar una cuenta personal autorizada. La captura previa mostraba una cuenta educativa con `Default Gemini Project` y cuatro claves existentes: **no reutilizarlas ni usarla para el piloto**.
2. Crear un proyecto nuevo llamado `RecepVoz-Gemini-Pilot` y comprobar su ID único. Si AI Studio exige importar un proyecto de Google Cloud, crearlo desde la cuenta personal y luego importarlo.
3. Comprobar explícitamente que el proyecto aparezca en **Free Tier**, sin cuenta de facturación vinculada, y que el modelo `gemini-3.5-flash-lite` tenga cuota gratuita disponible. Los modelos y cuotas pueden cambiar. No activar facturación.
4. Crear clave propia del proyecto, limitar el uso a la Gemini API si se ofrece esa opción y guardarla **solo localmente**. No subir la clave a GitHub, ChatGPT, capturas, Railway ni a proyectos de voz.
5. Usar un menú de restaurante y una lista de servicios completamente inventados, JPG/PNG/WEBP o PDF corto. En el nivel gratuito Google puede utilizar contenido para mejorar productos. No enviar información real de clientes.

## Prueba sin conexión ni claves

```bash
python3 scripts/pilot/gemini_import_probe.py ~/pilot/menu.png ~/pilot/servicios.jpg
python3 -m unittest discover -s scripts/pilot -p 'test_*.py' -v
```

Por defecto no envía ninguna solicitud. Admite 1 a 3 archivos diferentes, imágenes de hasta 512 KB y PDF de hasta 256 KB con 1 o 2 páginas. Para PDF instalar `pypdf` localmente.

## Requisito para la primera solicitud gratuita

Configurar estas variables **únicamente después de comprobar personalmente cada punto**, nunca por adelantado:

```text
GEMINI_PILOT_APPROVED=yes
GEMINI_PILOT_PERSONAL_PROJECT=yes
GEMINI_PILOT_FREE_TIER_CONFIRMED=yes
GEMINI_PILOT_NO_BILLING=yes
GEMINI_PILOT_SYNTHETIC_ONLY=yes
GEMINI_PILOT_PROJECT_NAME=RecepVoz-Gemini-Pilot
GEMINI_PILOT_PROJECT_ID=<ID del proyecto nuevo>
GEMINI_PILOT_API_KEY=<clave del proyecto nuevo>
```

**IMPORTANTE:** las banderas son comprobaciones humanas; el script no puede verificar la facturación del proveedor de forma remota. Si existe alguna duda, detenerse. Este flujo no tiene permiso para consumir el presupuesto de USD 2 reservado al piloto de OpenAI.

```bash
python3 scripts/pilot/gemini_import_probe.py --send \
  --audit ~/private-recepvoz-pilot/gemini-audit.jsonl \
  --report ~/private-recepvoz-pilot/gemini-review.jsonl \
  ~/pilot/menu.png ~/pilot/servicios.jpg
```

Las carpetas de auditoría y revisión deben estar fuera del repositorio. Nunca repetir automáticamente solicitudes inciertas. El ejecutor registra fases STARTED/RESPONSE/UNCERTAIN, modelo, tokens y SHA-256, y separa propuestas del registro financiero. No escribe en la base de datos ni activa IA comercial.

## Criterios de comparación contra OpenAI

Comparar ambos modelos con **las mismas imágenes sintéticas** y una tabla manual de nombres, precios, stock y duraciones explícitos. Aceptación: 0 datos inventados; si un campo es ilegible debe quedar ausente o con advertencia. Comparar latencia y tokens; comprobar en AI Studio Usage que no exista gasto de Gemini. Las pruebas unitarias solo verifican seguridad del ejecutor, no precisión real.

## Fuentes oficiales

- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
- https://ai.google.dev/gemini-api/docs/api-key
- https://ai.google.dev/gemini-api/docs/billing

**Producción sigue apagada** para importación pagada de imágenes/PDF. No habilitar `HELVOCA_BUSINESS_IMPORT_AI_ENABLED` ni modificar sus cuotas para este experimento.
