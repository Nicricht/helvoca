# RecepVoz Frontend Finalization V1

Fecha: 2026-09-28

## Objetivo

Cerrar el frontend comercial de RecepVoz como un producto coherente, oscuro, legible y listo para clientes, sin reescribir el backend ni introducir un framework frontend nuevo.

El criterio de éxito no es “tener más pantallas”. Es que una persona nueva pueda entrar, entender qué hace RecepVoz, configurar su negocio, operar reservas/clientes/inventario y ajustar a su recepcionista sin encontrarse con estilos contradictorios, conceptos técnicos o interfaces que parezcan pertenecer a productos distintos.

## Problema actual confirmado

El frontend está construido sobre HTML/CSS/JavaScript estático dentro de Spring Boot. La lógica funcional existe, pero la presentación se compone hoy de varias capas que se pisan:

- `styles.css`;
- `home-business.css`;
- `first-user-ux-v2.css`;
- `commercial-ui-v3.css`;
- estilos inline en páginas;
- estilos y copy modificados por JavaScript;
- CSS específico de páginas como Inventory, Sales, Pricing y Simulator.

Esto permite colisiones reales de especificidad, cambios de tema entre pantallas y regresiones donde un elemento queda con colores incompatibles.

## Decisión arquitectónica

### Opción elegida: consolidación controlada sobre el frontend existente

Mantener HTML/CSS/JavaScript y los contratos DOM/API actuales, pero consolidar la presentación en un sistema visual final único.

No se migrará a React, Vue, Tailwind ni otro framework. Una reescritura completa aumentaría el tiempo y el riesgo sin mejorar la lógica comercial existente.

Tampoco se seguirá agregando CSS de override indefinidamente. El objetivo es reducir y retirar capas antiguas a medida que cada pantalla queda migrada.

## Alcance

### Incluido

Pantallas de cliente y comerciales:

1. Landing pública.
2. Registro e ingreso.
3. Onboarding.
4. Inicio / Dashboard.
5. Reservas.
6. Clientes.
7. Inventario.
8. Recepcionista IA / simulador.
9. Configuración.
10. Pricing / contratación pública.
11. Estados vacíos, loading, errores y responsive compartido.
12. Header, navegación y marca compartidos.

### Fuera del rediseño comercial V1

- lógica backend;
- esquemas de base de datos;
- proveedores de voz, WhatsApp o pagos;
- `platform.html` y herramientas exclusivamente internas de administración;
- nuevas capacidades comerciales no existentes;
- llamadas, mensajes o pagos reales durante pruebas.

Las páginas internas deben continuar funcionando y no sufrir regresiones.

## Sistema visual definitivo

La experiencia de cliente será oscura. No se utilizará blanco puro como fondo dominante.

### Tokens

- fondo principal: `#0F1115`;
- superficie principal: `#151922`;
- superficie elevada: `#1B2230`;
- superficie interactiva: `#202735`;
- texto principal: `#F5F7FA`;
- texto secundario: `#A7B0BF`;
- texto tenue: `#7F8998`;
- marca primaria: `#6D5DF6`;
- marca secundaria: `#8B7CFF`;
- éxito: `#22C55E`;
- advertencia: `#F59E0B`;
- error: `#EF4444`;
- borde: `rgba(255,255,255,.08)`.

### Reglas

- un solo acento fuerte por superficie;
- sin fondos blancos completos;
- sin “galaxy backgrounds”;
- sin glassmorphism excesivo;
- sin gradientes decorativos salvo dentro del logotipo de marca;
- sin waveforms ni robots decorativos como relleno;
- radio estándar: 12–18 px;
- espacios basados en múltiplos de 8 px;
- sombras discretas;
- foco visible accesible;
- texto normal mínimo 14 px en acciones críticas;
- contraste verificable.

## Marca

El logotipo entregado por el usuario pasa a ser la referencia de marca.

Se utilizarán tres expresiones:

1. logo completo para material amplio;
2. marca compacta para header;
3. isotipo para favicon/PWA/avatar cuando corresponda.

La cabecera comercial utiliza una versión compacta, no el texto plano `RECEPVOZ`.

## Arquitectura CSS final

Crear una capa de diseño canónica y migrar pantallas hacia ella.

### Responsabilidades

`styles.css`
- reset;
- tipografía;
- utilidades mínimas;
- contratos estructurales globales que no expresen tema.

Nueva capa canónica, nombre de trabajo `recepvoz-ui.css`:
- tokens;
- shell;
- header;
- navegación;
- botones;
- inputs;
- cards/panels;
- tablas;
- badges;
- drawers;
- dialogs;
- estados;
- responsive;
- accesibilidad.

CSS de página:
- solo layout o componentes exclusivos de esa página;
- no redefine colores globales, botones globales ni navegación.

### Retirada progresiva

Durante la migración:
- eliminar overrides obsoletos de `commercial-ui-v3.css`;
- retirar estilos duplicados de `first-user-ux-v2.css`;
- reducir estilos inline de `index.html` y `settings.html`;
- evitar que JavaScript inyecte estilos permanentes;
- mantener clases/IDs que usa la lógica JS y los tests salvo necesidad demostrada.

Al finalizar no debe existir una cadena de “tema base → tema viejo → override → override del override”.

## Arquitectura de navegación

### Cliente listo

- Inicio
- Reservas
- Clientes
- Inventario
- Recepcionista IA
- Configuración

`Recepcionista IA` reutiliza el simulador/flujo de voz existente en vez de crear un subsistema paralelo.

### Primer uso

Durante onboarding se muestra solo lo necesario para completar:
- negocio;
- servicios;
- horarios;
- recepcionista.

Inventario y herramientas operativas completas permanecen ocultas hasta que el negocio esté suficientemente configurado.

### Interno

Diagnósticos, provisioning, providers, webhooks, entitlements, sandbox y certificaciones no aparecen en navegación normal de cliente.

## Pantalla 1: Landing

La Landing se compone de bloques deliberadamente limitados:

1. header con logo;
2. Hero;
3. franja de confianza;
4. Cómo funciona;
5. funciones principales;
6. preview operacional;
7. pricing/CTA final;
8. footer legal.

La PR #612 sirve como referencia ya certificada para header, Hero y franja de confianza. Su implementación deberá preservarse o portarse al branch final sin depender de un merge inseguro.

## Pantalla 2: Auth

Registro e ingreso dejan de parecer una tarjeta administrativa.

Registro:
- Nombre del negocio;
- Correo;
- Contraseña;
- CTA único.

Ingreso:
- Correo;
- Contraseña;
- CTA único.

Mantener payloads y validaciones actuales. Errores se muestran dentro del formulario con lenguaje humano.

## Pantalla 3: Onboarding

Máximo cuatro pasos visibles:

1. Tu negocio.
2. Servicios.
3. Horarios.
4. Recepcionista.

Una acción dominante por estado. No exponer configuración avanzada durante el primer recorrido.

## Pantalla 4: Inicio

Debe responder rápidamente:

- ¿RecepVoz está atendiendo?
- ¿Qué pasó hoy?
- ¿Qué necesita atención?
- ¿Qué hago ahora?

Contenido:
- estado de la recepcionista;
- llamadas/interacciones recientes cuando estén disponibles;
- reservas próximas;
- clientes o solicitudes que necesitan acción;
- métricas operativas reales;
- acceso directo a prueba/configuración de la recepcionista.

No mostrar métricas inventadas.

## Pantalla 5: Reservas

Experiencia tipo agenda/operación, no base de datos cruda.

Desktop:
- tabla compacta;
- filtros;
- detalle lateral.

Móvil:
- tarjetas;
- filtros compactos;
- detalle de pantalla completa.

Preservar toda trazabilidad existente hacia conversación/origen cuando esté disponible.

## Pantalla 6: Clientes

CRM ligero:

- nombre;
- contacto;
- última interacción;
- próxima reserva/estado relevante;
- historial accesible.

Evitar campos técnicos como identificadores internos en la vista normal.

## Pantalla 7: Inventario

Preservar toda la lógica certificada.

Rediseño únicamente visual:
- misma navegación;
- KPIs discretos;
- tabla legible;
- alertas de stock;
- modales consistentes con el resto del producto.

## Pantalla 8: Recepcionista IA

Reutilizar `simulator.html` y configuración existente como experiencia de producto.

Debe concentrar:
- estado;
- voz;
- saludo;
- comportamiento;
- prueba simulada;
- enlace a configuración avanzada cuando corresponda.

No realizar llamadas reales.

## Pantalla 9: Configuración

Estructura por categorías humanas:

- Negocio
- Horarios
- Servicios
- Recepcionista
- Equipo
- Integraciones
- Facturación

Las capacidades existentes se reubican, no se duplican.

Los controles técnicos avanzados permanecen detrás de disclosure explícito.

## Pricing y contratación

Pricing mantiene únicamente:
- planes;
- límites/cobertura;
- CTA claro;
- estado de contratación cuando exista.

No activar pagos live en pruebas.

## Estados compartidos

Todos los módulos deben definir:

- loading;
- vacío;
- error recuperable;
- error de autenticación;
- éxito;
- acción bloqueada.

Un fallo parcial no debe vaciar la pantalla completa cuando otros módulos tienen datos válidos.

## Responsive

Breakpoints de validación obligatorios:

- 390 px;
- 768 px;
- 1440 px.

Requisitos:
- cero overflow horizontal no intencional;
- navegación utilizable;
- targets táctiles suficientes;
- tablas se convierten o contienen de forma explícita;
- dialogs/drawers no salen del viewport;
- CTA primario visible.

## Accesibilidad

- foco visible;
- labels asociados;
- botones y enlaces semánticos;
- contraste suficiente;
- estados no comunicados solo por color;
- drawers/dialogs con manejo correcto de foco cuando el código existente lo permita;
- textos alternativos de marca.

## Estrategia de migración

La implementación se hará en bloques cerrados sobre una rama dedicada.

Orden de trabajo en grupos visibles de tres pantallas:

### Bloque 1
1. Landing.
2. Login/Registro.
3. Onboarding.

### Bloque 2
4. Inicio.
5. Reservas.
6. Clientes.

### Bloque 3
7. Inventario.
8. Recepcionista IA.
9. Configuración.

### Cierre transversal
10. Pricing y páginas públicas auxiliares.
11. eliminación de CSS muerto/overrides + responsive/accesibilidad global.
12. certificación completa.

La base visual canónica (tokens, shell, header, navegación y componentes compartidos) se implementa de forma incremental dentro del primer bloque que la necesite; no se considera una pantalla adicional.

Al terminar cada grupo de tres pantallas:
- mostrar al usuario qué cambió en cada pantalla antes de continuar;
- incluir evidencia visual cuando sea posible y, como mínimo, los cambios concretos de estructura/estilo;
- reportar pruebas dirigidas y estado del gate;
- no iniciar el siguiente grupo hasta que el usuario indique continuar.

Cada bloque:
- prueba/regresión primero;
- implementación mínima;
- Fast Gate;
- revisión visual/adversarial;
- commit.

Full Gate se ejecuta al cierre de cada grupo de tres pantallas y obligatoriamente antes de merge.

## Estrategia de tests

Mantener toda la suite actual y añadir contratos visuales/estructurales sin screenshots frágiles.

Cobertura E2E:

- navegación correcta por rol/estado;
- paleta oscura canónica;
- logo y header;
- no overflow en 390/768/1440;
- onboarding simplificado;
- páginas operativas accesibles;
- formularios funcionales;
- inventory sin regresiones;
- simulator sin llamadas reales;
- configuración conserva persistencia;
- pricing no dispara cobros;
- mensajes de error/empty states visibles.

## Criterios de aceptación final

El frontend se considera terminado solo cuando:

1. Todas las pantallas de cliente usan el mismo sistema oscuro.
2. No existe blanco puro como superficie dominante.
3. No hay estilos globales contradictorios entre capas.
4. Ninguna página de cliente parece pertenecer a otro producto.
5. La navegación normal contiene como máximo las seis áreas definidas.
6. El primer uso no expone herramientas avanzadas.
7. La marca RecepVoz es consistente.
8. No existe overflow en los tres viewports objetivo.
9. No se rompe ningún flujo funcional existente.
10. Fast Gate y Full Gate están verdes en el HEAD exacto.
11. La rama está 0 commits detrás de `main` antes de merge.
12. Merge y deploy siguen requiriendo autorización explícita del usuario.

## Resultado esperado

El usuario debe percibir una sola aplicación:

“Mi recepcionista está funcionando. Aquí veo lo que pasó. Aquí manejo reservas y clientes. Aquí cambio cómo trabaja.”

Todo lo demás queda subordinado a esa experiencia.
