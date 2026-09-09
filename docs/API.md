# API Sprint 1

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/auth/me` | Authenticated |
| GET | `/api/v1/business` | BUSINESS_ADMIN / OPERATOR |
| PATCH | `/api/v1/business` | BUSINESS_ADMIN |
| GET | `/api/v1/admin/users` | BUSINESS_ADMIN |
| POST | `/api/v1/admin/users` | BUSINESS_ADMIN |
| GET | `/actuator/health` | Public |
| GET | `/swagger-ui.html` | Public in development |
