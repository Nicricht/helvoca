# Sprint 1 - Fundación

## Objetivo

Dejar una base segura y multi-tenant sobre la cual agregar telefonía, IA y reservas sin rehacer autenticación ni aislamiento de datos.

## Historias cubiertas

### S1-HU-01 Autenticación
Como usuario administrativo quiero iniciar sesión para acceder de forma segura al sistema.

Criterios:
- Credenciales validadas con BCrypt.
- Se emite JWT firmado HS256.
- JWT contiene `sub`, `email`, `roles` y `business_id` cuando aplica.
- Endpoints protegidos rechazan peticiones sin token.

### S1-HU-02 Aislamiento multi-tenant
Como negocio quiero que mis datos estén aislados de los demás tenants.

Criterios:
- El tenant se obtiene desde JWT.
- El frontend no define el tenant efectivo.
- Los servicios de negocio usan el tenant autenticado.

### S1-HU-03 Roles
Como plataforma quiero separar permisos de plataforma, administrador de negocio y operador.

Criterios:
- Roles globales sembrados con Flyway.
- `BUSINESS_ADMIN` puede administrar usuarios de su tenant.
- `OPERATOR` no puede crear usuarios.
- `/api/v1/platform/**` queda reservado a `PLATFORM_ADMIN`.

### S1-HU-04 Configuración del negocio
Como administrador quiero consultar y actualizar los datos básicos de mi negocio.

Criterios:
- GET `/api/v1/business`.
- PATCH `/api/v1/business`.
- Auditoría al modificar.

## Definition of Done

- Código compila con Java 21.
- Migraciones versionadas con Flyway.
- Contenedores definidos.
- No hay secretos reales versionados.
- Swagger disponible.
- Tests unitarios incluidos.
- Tenant nunca se selecciona desde un parámetro del cliente para operaciones normales.
