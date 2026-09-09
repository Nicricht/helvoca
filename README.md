# Helvoca - Sprint 1

Base técnica del SaaS multi-tenant de call center con IA.

## Incluido

- Java 21
- Spring Boot 4.1.1
- Spring Security + JWT HS256
- PostgreSQL
- Flyway
- Docker Compose
- Roles: `PLATFORM_ADMIN`, `BUSINESS_ADMIN`, `OPERATOR`
- Tenant obtenido desde el JWT, no desde parámetros del frontend
- CRUD mínimo del negocio actual
- Creación/listado de usuarios del negocio
- Auditoría inicial
- Swagger UI
- Tests unitarios iniciales

## Requisitos

Opción A: Docker + Docker Compose.

Opción B: Java 21, Maven y PostgreSQL 16+.

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

Cuando se inicia mediante `compose.yml`, se crea automáticamente:

```text
Email: admin@helvoca.local
Password: ChangeMe123!
```

Cambiar estas credenciales antes de cualquier despliegue real.

## Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@helvoca.local","password":"ChangeMe123!"}'
```

Copia `accessToken` y úsalo así:

```bash
curl http://localhost:8080/api/v1/business \
  -H "Authorization: Bearer TU_TOKEN"
```

## Actualizar negocio

```bash
curl -X PATCH http://localhost:8080/api/v1/business \
  -H "Authorization: Bearer TU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Restaurante Central","timezone":"America/Santiago","language":"es"}'
```

## Crear operador

```bash
curl -X POST http://localhost:8080/api/v1/admin/users \
  -H "Authorization: Bearer TU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Operador Uno","email":"operator@helvoca.local","password":"ChangeMe123!","roles":["OPERATOR"]}'
```

## Regla multi-tenant

Los controladores de negocio no aceptan `businessId` para decidir el tenant. El backend lo extrae del claim `business_id` del JWT mediante `TenantProvider`.

## Próximo sprint

1. Customer
2. Service
3. Booking
4. Availability
5. Knowledge Base
6. Tests de integración con PostgreSQL/Testcontainers
