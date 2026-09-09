# Helvoca - Sprint 2

Helvoca es la base de un SaaS multi-tenant de atención telefónica con IA para empresas. El backend ya cuenta con autenticación, aislamiento por tenant y el núcleo operativo de clientes, servicios, reservas y conocimiento empresarial.

## Estado actual

### Plataforma y seguridad

- Java 21
- Spring Boot 4.1.1
- Spring Security + JWT HS256
- PostgreSQL 16+
- Flyway
- Docker Compose
- Swagger/OpenAPI
- Roles `PLATFORM_ADMIN`, `BUSINESS_ADMIN` y `OPERATOR`
- Tenant obtenido desde el claim `business_id` del JWT
- Auditoría de operaciones
- Manejo global de errores

### Sprint 2

- Clientes
  - crear
  - listar
  - consultar por id
  - buscar por teléfono
  - actualizar
- Catálogo de servicios
  - crear
  - listar
  - consultar
  - actualizar
  - desactivar
  - duración y precio
- Reservas
  - crear
  - listar
  - consultar
  - verificar disponibilidad
  - calcular término según duración del servicio
  - rechazar solapamientos
  - reprogramar
  - cancelar
  - rechazar reservas en el pasado
- Knowledge Base
  - crear
  - listar
  - consultar
  - actualizar
  - filtrar activos
  - desactivar
- Aislamiento multi-tenant en todos los módulos
- Autorización por roles
- Tests unitarios
- Test de integración con PostgreSQL mediante Testcontainers
- CI con GitHub Actions

## Regla crítica multi-tenant

El frontend no decide el negocio mediante un parámetro `businessId` confiable.

El backend obtiene el tenant autenticado desde el claim `business_id` del JWT mediante `TenantProvider` y todas las consultas de negocio se filtran con ese identificador.

## Regla crítica de reservas

Una reserva solo puede crearse cuando cliente y servicio pertenecen al tenant autenticado y el horario solicitado está disponible.

Para el MVP, cada servicio representa una única capacidad reservable. Personal, salas, mesas, sillas o equipamiento se modelarán posteriormente como recursos explícitos.

## Base de datos

Flyway aplica actualmente:

- `V1__foundation.sql`
- `V2__seed_roles.sql`
- `V3__sprint2_core.sql`

La tercera migración incorpora `customer`, `service`, `booking` y `knowledge_item` con claves foráneas, índices por tenant y controles básicos de integridad.

## Ejecutar con Docker

```bash
cp .env.example .env
docker compose up --build
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Health:

```text
http://localhost:8080/actuator/health
```

## Usuario local de desarrollo

Cuando el seed de desarrollo está habilitado:

```text
Email: admin@helvoca.local
Password: ChangeMe123!
```

Estas credenciales son exclusivamente de desarrollo y deben cambiarse antes de cualquier despliegue real.

## Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@helvoca.local","password":"ChangeMe123!"}'
```

Usa luego el `accessToken` como Bearer token.

## Documentación

- `docs/SPRINT1.md`
- `docs/SPRINT2.md`
- `docs/API.md`

## CI

Cada push y pull request hacia `main` ejecuta Maven y la suite de tests. La prueba de integración inicia PostgreSQL 16 con Testcontainers, aplica Flyway y valida consultas reales de reservas.

## Próximo sprint

Sprint 3 incorpora la capa telefónica:

1. números telefónicos por negocio
2. sesiones de llamada
3. estados de llamada
4. webhooks de telefonía
5. validación de firma de Twilio
6. base de integración con Twilio Programmable Voice
7. WebSocket / Media Streams
8. persistencia de transcripciones

Después de esta capa se conectará el audio con el agente de voz IA en tiempo real.
