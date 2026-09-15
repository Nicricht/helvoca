# V30 runtime enforcement

V30 separa la política de automatización del flujo de aprobación humana.

## Qué sí se aplica en runtime

- `autoExecute` se resuelve por tenant y tipo de operación.
- `AiAgentService` oculta herramientas mutantes cuando `autoExecute=false`.
- `BusinessOperationCapabilityService` aplica la misma restricción a las herramientas comerciales.
- voz y WhatsApp vuelven a comprobar la política justo antes de ejecutar una mutación.
- las herramientas de lectura/estado permanecen disponibles.
- ORDER, DELIVERY, BOOKING y PAYMENT mantienen confirmación explícita del cliente como piso de seguridad.

Esto significa que el flujo normal es:

cliente -> backend valida -> cliente confirma cuando corresponde -> Helvoca ejecuta -> Event Log

No existe una etapa rutinaria de aprobación de un empleado.

## Qué V30 no simula

`retryPolicy`, `maxAutoRetries`, `paymentRequirement` y `escalationPolicy` ya son decisiones persistidas y resueltas por `OperationPolicyService`, pero V30 no inventa side effects que todavía no ocurrieron.

Los siguientes subsistemas deben consumir esas decisiones en iteraciones posteriores:

- orquestador de reintentos seguros;
- payment chaining entre operaciones;
- Human Handoff persistente.

Hasta entonces, V30 nunca afirma que reintentó, cobró o escaló solo porque una política lo permita.
