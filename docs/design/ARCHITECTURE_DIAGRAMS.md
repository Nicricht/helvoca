# Helvoca - Diagramas técnicos de texto

## 1. Arquitectura de producción

```mermaid
flowchart LR
    C[Cliente por teléfono]
    T[Proveedor telefónico]
    WH[Webhook HTTP]
    WS[Gateway WebSocket de audio]
    AI[Proveedor IA Realtime]
    API[Backend Spring Boot]
    DB[(PostgreSQL)]
    R[(Redis)]
    MQ[(RabbitMQ)]
    ADM[Panel web]
    EXT[Calendario / CRM / ERP]
    OP[Operador humano]

    C --> T
    T --> WH
    WH --> API
    API --> DB
    API --> R
    API --> MQ
    T <--> WS
    WS <--> AI
    AI -->|tool call| API
    API -->|resultado real| AI
    API <--> EXT
    ADM --> API
    API --> OP
    T --> OP
```

## 2. Secuencia de llamada

```mermaid
sequenceDiagram
    participant C as Cliente
    participant T as Telefonía
    participant B as Backend
    participant W as Audio WS
    participant A as IA Realtime
    participant D as PostgreSQL

    C->>T: Llama al número del negocio
    T->>B: Webhook de llamada entrante
    B->>B: Validar firma
    B->>D: Buscar número y tenant
    B->>D: Crear call_session
    B-->>T: TwiML Connect/Stream
    T->>W: Abrir WebSocket
    W->>A: Crear sesión Realtime

    loop Conversación
        C->>T: Voz
        T->>W: Audio
        W->>A: Audio
        A-->>W: Audio de respuesta
        W-->>T: Audio
        T-->>C: Voz IA
    end

    A->>B: Tool call create_booking
    B->>D: Validar tenant + disponibilidad + insertar
    D-->>B: Resultado real
    B-->>A: SUCCESS o error de dominio
    A-->>C: Comunicar resultado real
```

## 3. Tool call controlada

```mermaid
flowchart TD
    A[IA solicita tool] --> C{Capability habilitada?}
    C -- No --> D[CAPABILITY_NOT_ALLOWED]
    C -- Sí --> V[Validar argumentos]
    V --> T[Resolver tenant desde sesión]
    T --> S[Servicio de aplicación]
    S --> R{Regla de negocio válida?}
    R -- No --> E[Error de dominio]
    R -- Sí --> P[(Transacción PostgreSQL)]
    P --> O[Resultado]
    O --> A2[Devolver resultado a IA]
    E --> A2
    D --> A2
```

## 4. Seguridad multi-tenant

```mermaid
flowchart LR
    U[Usuario]
    JWT[JWT]
    F[TenantContextFilter]
    P[Permisos / Roles]
    C[Controller]
    S[Service]
    R[Repository]
    DB[(PostgreSQL)]

    U --> JWT --> F
    F --> P
    P --> C --> S --> R --> DB
    F -. business_id .-> S
    F -. business_id .-> R
```

Regla: el frontend no decide el tenant. `business_id` se toma de la identidad autenticada.

## 5. Reserva concurrente

```mermaid
sequenceDiagram
    participant A as Llamada A
    participant B as Llamada B
    participant S as BookingService
    participant P as PostgreSQL

    A->>S: reservar 18:00 recurso X
    B->>S: reservar 18:00 recurso X
    S->>P: INSERT A
    S->>P: INSERT B
    P-->>S: A OK
    P-->>S: B viola exclusión de solapamiento
    S-->>A: 201 CONFIRMED
    S-->>B: 409 BOOKING_SLOT_UNAVAILABLE
```

## 6. Transferencia humana

```mermaid
flowchart TD
    A[IA no resuelve / cliente pide humano] --> C[Crear human_transfer]
    C --> O{Operador disponible?}
    O -- Sí --> X[Enviar contexto al operador]
    X --> T[Transferir llamada]
    T --> F[Completar atención]
    O -- No --> M[Informar indisponibilidad]
    M --> R[Crear callback_request]
```
