# Helvoca - Paquete de diseño MVP

Esta carpeta contiene el diseño técnico consolidado del MVP de Helvoca.

## Contenido

- `README_IMPLEMENTACION.md`: arquitectura, stack, módulos y reglas de implementación.
- `BACKLOG_MVP.md`: historias de usuario, criterios de aceptación y orden de sprints.
- `ARCHITECTURE_DIAGRAMS.md`: diagramas Mermaid de arquitectura, llamada, seguridad, concurrencia y transferencia humana.
- `CLASS_DIAGRAM.md`: diagrama de clases del backend.
- `AI_TOOL_CONTRACTS.md`: contratos de las tools controladas que la IA puede ejecutar.
- `WIREFRAMES.md`: wireframes de texto del panel web.
- `TEST_CASES.md`: casos de prueba críticos del MVP.
- `schema_final.sql`: modelo PostgreSQL consolidado.
- `docker-compose.yml`: entorno PostgreSQL + Redis + RabbitMQ del diseño.
- `.env.example`: variables necesarias, sin secretos reales.
- `openapi.yaml.gz`: contrato OpenAPI 3.1 completo comprimido.

## Restaurar OpenAPI

Linux/macOS:

```bash
gzip -dk docs/design/openapi.yaml.gz
```

PowerShell con gzip disponible:

```powershell
gzip -dk .\docs\design\openapi.yaml.gz
```

El archivo resultante es `docs/design/openapi.yaml`.

## Reglas clave

1. `BUSINESS` es el tenant.
2. El tenant se obtiene desde la identidad autenticada, no desde un identificador confiado del frontend.
3. La IA no escribe directamente en PostgreSQL.
4. El backend es la fuente de verdad para reservas, pedidos e integraciones.
5. Las operaciones con efectos persistentes son idempotentes.
6. PostgreSQL impide reservas concurrentes sobre el mismo recurso y franja.
7. Webhooks de telefonía deben validar la firma del proveedor.
8. Toda acción sensible queda auditada.
