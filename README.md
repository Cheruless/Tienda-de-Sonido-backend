# Tienda Sonido Back

Monorepo de microservicios para la tienda de instrumentos musicales **Sonido Vivo** (plan DSY1104 - Forma B).
Reúne cuatro servicios Spring Boot que se comunican por HTTP y donde cada uno es dueño de sus datos.

| Servicio | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | — (sin estado) | Único punto de entrada; enruta `/api/**` al servicio correcto. |
| `auth-service` | 8081 | PostgreSQL 16 (`auth_db`) | Identidad: registro, login, roles y emisión de JWT. |
| `catalog-service` | 8082 | MySQL 8 (`tienda_catalogo`) | Catálogo de productos y categorías; descuenta stock en la venta. |
| `cart-service` | 8083 | MongoDB 8 (`carts`) | Carrito por usuario y compra (coordina con catálogo). |

---

## 1. Qué hace el proyecto

Implementa el backend completo de una tienda de música:

1. **Identidad** (`auth-service`): registra usuarios, autentica con correo y contraseña, asigna roles
   (`ADMIN` / `CLIENTE`) y emite el JWT que usa todo el sistema.
2. **Catálogo** (`catalog-service`): expone productos y categorías de forma pública y permite al
   administrador crearlos, editarlos y eliminarlos. Además registra la venta y **descuenta el stock**.
3. **Carrito** (`cart-service`): mantiene un carrito por usuario, copia nombre y precio de catálogo
   al agregar (snapshot) y ejecuta la compra.
4. **Gateway** (`api-gateway`): puerta de entrada única. El frontend solo conoce `http://<host>:8080`;
   el gateway reparte cada ruta hacia el servicio correspondiente.

El resultado es un flujo de compra completo: registrarse, iniciar sesión, ver el catálogo, armar el
carrito y pagar, con el stock descontado de forma consistente.

---

## 2. Modelo de flujo de operaciones simplificado

### Topología

```
                         ┌──────────────────────────┐
                         │   Cliente (frontend)     │
                         └────────────┬─────────────┘
                                      │ HTTP :8080  (Authorization: Bearer <JWT>)
                         ┌────────────▼─────────────┐
                         │       api-gateway        │
                         │  enrutamiento por /api/**│
                         └──┬───────┬───────┬───────┘
        /api/auth/** ───────┘       │       └─────── /api/cart/**
        /api/users/**              │                (cart-service :8083)
                    ┌───────────────▼──────────┐           │
                    │      auth-service :8081  │           │ compra
                    │  emite y valida identidad│           ▼
                    └───────────────┬──────────┘   ┌──────────────────────┐
                                    │              │   catalog-service    │
                          ┌─────────▼─────────┐    │      :8082           │
                          │  auth-db          │    │  GET público         │
                          │  PostgreSQL 16    │    │  CRUD → ADMIN        │
                          │  :5433            │    │  /venta → descuenta  │
                          └───────────────────┘    └──────────┬───────────┘
                                                              │
                                                     ┌────────▼─────────┐
                                                     │  catalog-db      │
                                                     │  MySQL 8 :3307   │
                                                     └──────────────────┘
                                     ┌────────────────────┴───────────────┐
                                     │  cart-service :8083               │
                                     │  agregar / quitar / comprar       │
                                     └────────────────────┬───────────────┘
                                                          │
                                                 ┌────────▼─────────┐
                                                 │  cart-db         │
                                                 │  MongoDB 8 :27018│
                                                 └──────────────────┘
```

### Flujo de compra extremo a extremo

```
1) POST /api/auth/login  ─────────────────► auth-service
                                            valida correo + BCrypt
                                            emite JWT (sub = email, roles, exp 1h)
2) GET  /api/productos   ─────────────────► catalog-service   (público)
3) POST /api/cart/items  ─────────────────► cart-service
        {productId, quantity}              valida JWT → userId = email
                                            GET /api/productos/{id}  (snapshot nombre/precio)
                                            guarda en MongoDB (@Version + reintento)
4) POST /api/cart/compra ─────────────────► cart-service
                                            POST /api/productos/venta (reenvía el JWT)
                                            ▼
                                       catalog-service
                                            descuenta stock en UNA transacción
                                            (todo o nada; 409 si falta stock)
                                            ▼
                                       cart-service vacía el carrito
```

### Roles y visibilidad

| Operación | Quién |
|---|---|
| Ver productos y categorías | cualquiera (público) |
| Crear, editar o eliminar productos/categorías | `ADMIN` |
| Registrar la venta (descuento de stock) | cualquier usuario autenticado |
| Carrito y compra | cualquier usuario autenticado (solo el suyo) |
| Gestión de usuarios | `ADMIN` |

---

## 3. Decisiones de diseño

### Stack

- **Spring Boot 4.1.2 + Java 21 + Maven**, con wrapper (`mvnw`) en cada servicio.
- **Spring Security** (JWT), **Spring Data JPA** (auth, catálogo), **Spring Data MongoDB** (cart),
  **Spring Cloud Gateway** (gateway), **springdoc OpenAPI**.
- Tres motores de datos: **PostgreSQL 16**, **MySQL 8** y **MongoDB 8**.
- **Docker Compose** como unidad de despliegue (pensado para una sola instancia EC2).

### Decisiones arquitectónicas

1. **Monorepo con `pom.xml` agregador.** El root declara los cuatro módulos; cada servicio se buildea
   por separado (`./mvnw -pl <servicio> test`) o en conjunto (`./mvnw verify`).
2. **Persistencia políglota (cada servicio es dueño de sus datos).**
   - `auth`: PostgreSQL relacional (usuarios, roles, tabla de relación).
   - `catalog`: MySQL relacional (productos con clave foránea a categorías, restricciones únicas).
   - `cart`: MongoDB documental (un documento por carrito con los ítems embebidos).
3. **JWT HS256 con secreto compartido.** `auth-service` firma; `catalog` y `cart` verifican con el
   mismo `JWT_SECRET` (variable de entorno, nunca commiteada). Es el esquema más simple para un solo
   host; la rampa de salida a RS256 + JWKS queda anotada como deuda.
4. **Comunicación HTTP síncrona.** Sin service discovery ni mensajería: los nombres DNS de Compose
   (`catalog-service`, `cart-service`) y URLs por variable de entorno bastan para una EC2 única.
5. **El gateway no valida el token.** Solo enruta y reenvía `Authorization`; cada servicio con datos
   sensibles aplica su propia seguridad (defensa en profundidad: directo por la red interna también
   queda protegido).
6. **Identidad = `sub` del JWT = correo electrónico.** Nunca viaja en la URL ni en el body; el carrito
   y el usuario se derivan del token.
7. **CORS con lista explícita de orígenes** (`CORS_ORIGINS`), nunca `*`. El gateway elimina el header
   `Origin` antes de reenviar para evitar conflictos con el CORS de los servicios.
8. **`ddl-auto: update` + seeders idempotentes** (comodidad de desarrollo). No hay migraciones
   Flyway/Liquibase; se deja como mejora futura.
9. **Consistencia de la compra:** cart envía la venta como lote a catálogo, que la descuenta en una
   transacción atómica (todo o nada). Solo si catálogo confirma, cart vacía el carrito. El carrito
   usa `@Version` + reintento + índice único para no perder escrituras concurrentes.
10. **Docker Compose define 7 contenedores** (3 BD + 4 servicios) con healthchecks, `depends_on` y
    volúmenes con nombre. `.env.example` es la plantilla; `.env` está ignorado por git.

### Cómo levantar el stack

```bash
cp .env.example .env       # ajusta JWT_SECRET (>= 32 caracteres)
docker compose up -d --build
```

| Servicio | URL |
|---|---|
| Gateway | http://localhost:8080 |
| auth-service | http://localhost:8081 |
| catalog-service | http://localhost:8082 |
| cart-service | http://localhost:8083 |

Usuarios iniciales del seed de auth: `admin@sonidovivo.cl` / `Admin123Password` (ADMIN) y
`demo@sonidovivo.cl` / `Demo123Password` (CLIENTE). El catálogo se siembra con 10 categorías y 51
productos.

---

## 4. Autor y colaboradores

| Rol | Persona |
|---|---|
| Scrum Master y desarrollador | Exequiel Pulgar |
| Desarrollador | Fabian Valencia |
| Desarrollador | Jordan Quezada |

**Colaboradores del proyecto:** Exequiel Pulgar, Fabian Valencia y Jordan Quezada.

| Servicio | Autor |
|---|---|
| `auth-service` | Jordan Quezada |
| `cart-service` | Exequiel Pulgar |
| `catalog-service` | Fabian Valencia |
| `api-gateway` | Exequiel Pulgar, Fabian Valencia y Jordan Quezada |
