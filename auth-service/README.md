# auth-service

Microservicio de **identidad** de Tienda Sonido: registra usuarios, valida credenciales, administra
roles y **emite el JWT** que usan `catalog-service` y `cart-service` para autenticar cada petición.

- Puerto: `8081`
- Base de datos: PostgreSQL 16 (`auth_db`), mapeada con Spring Data JPA
- Seguridad: Spring Security + JWT HS256 (jjwt)
- Documentación interactiva: `/swagger-ui.html` y `/v3/api-docs`

---

## 1. Qué hace

1. **Registro** (`POST /api/auth/register`): crea un usuario con rol `CLIENTE`. Valida nombre, correo
   y contraseña; si el correo ya existe responde `409`.
2. **Login** (`POST /api/auth/login`): autentica con correo + contraseña (BCrypt) y devuelve un JWT
   HS256 con `sub` = correo, claim `roles`, `iat` y `exp` (1 hora).
3. **Perfil** (`GET /api/users/me`): devuelve los datos del usuario autenticado.
4. **Gestión de usuarios** (solo `ADMIN`):
   - `GET /api/users` — lista todos los usuarios.
   - `PATCH /api/users/{id}/activo` — activa/desactiva.
   - `PUT /api/users/{id}/roles` — reemplaza los roles.
   - `PUT /api/users/{id}` — actualiza nombre y contraseña (el correo no se puede cambiar).
   - `DELETE /api/users/{id}` — elimina un usuario.
5. **Datos iniciales**: al arrancar crea los roles `ADMIN` y `CLIENTE`, y los usuarios
   `admin@sonidovivo.cl` / `Admin123Password` (ADMIN) y `demo@sonidovivo.cl` / `Demo123Password`
   (CLIENTE) si no existen.

### Endpoints

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | público | Devuelve `token`, `type`, `expiresInSeconds` y el `usuario`. `401` si las credenciales fallan. |
| POST | `/api/auth/register` | público | Crea un usuario `CLIENTE`. `201`; `409` si el correo ya existe. |
| GET | `/api/users/me` | autenticado | Datos del usuario del token. |
| GET | `/api/users` | `ADMIN` | Lista usuarios. |
| PATCH | `/api/users/{id}/activo` | `ADMIN` | Cambia el estado activo. |
| PUT | `/api/users/{id}/roles` | `ADMIN` | Reemplaza roles (`ADMIN`/`CLIENTE`). |
| PUT | `/api/users/{id}` | `ADMIN` | Actualiza nombre y contraseña. |
| DELETE | `/api/users/{id}` | `ADMIN` | Elimina el usuario. |

---

## 2. Modelo de flujo de operaciones simplificado

### Registro y login

```
Cliente ──POST /api/auth/register──► AuthController
                                       │ valida @Valid (nombre, email, password)
                                       │ email en minúsculas, BCrypt
                                       ▼
                                   UsuarioRepository ──► PostgreSQL (usuarios, usuario_roles)
                                       │
                                       └─ 201  {message}        409 si el correo ya existe

Cliente ──POST /api/auth/login─────► AuthController
                                       │ email en minúsculas
                                       ▼
                                   AuthenticationManager
                                       ▼
                                   CustomUserDetailsService ──► PostgreSQL (busca por email)
                                       ▼
                                   BCrypt verifica la contraseña
                                       ▼
                                   JwtUtil.generateToken(sub=email, roles, exp=1h)
                                       │
                                       └─ 200 {token, type:"Bearer", expiresInSeconds, usuario}
```

### Uso del token en el resto del sistema

```
catalog-service / cart-service
        │  reciben Authorization: Bearer <JWT>
        │  verifican la firma HS256 con el mismo JWT_SECRET
        ▼
   sub = email del usuario  ──►  identidad y dueño del carrito
```

### Gestión de usuarios (ADMIN)

```
PATCH /api/users/{id}/activo ─► no puedes desactivarte a ti mismo
PUT    /api/users/{id}/roles ─► no puedes quitarte tu propio rol ADMIN
PUT    /api/users/{id}        ► el correo es inmutable; no puedes degradarte si eres el último ADMIN
DELETE /api/users/{id}        ► no puedes eliminarte a ti mismo; no puedes eliminar al último ADMIN
```

---

## 3. Decisiones de diseño

1. **JWT HS256 con jjwt.** Firma simétrica con un secreto compartido (`JWT_SECRET`, mínimo 32 bytes).
   Es el esquema más simple para un solo host; la migración a RS256 + JWKS queda anotada.
2. **`sub` = correo electrónico.** La identidad del usuario es su correo (estable y único). Por eso el
   correo **no se puede modificar** en `PUT /api/users/{id}`: cambiarlo rompería el token y el carrito.
3. **Un rol efectivo por usuario.** La API expone un solo `rol` (`admin` o `cliente`); internamente se
   guardan en una tabla `usuario_roles` para no acoplar la API a la estructura de datos.
4. **Protección del último ADMIN.** No se puede eliminar/desactivar/degradar al último administrador,
   ni uno mismo: evita dejar la tienda sin quien la administre.
5. **BCrypt para las contraseñas.** Nunca se guardan ni se devuelven en texto plano.
6. **Stateless + CSRF desactivado.** No hay sesión; el token viaja en cada petición. Sin sesión el
   vector CSRF no aplica.
7. **401 sin redirect.** `HttpStatusEntryPoint(UNAUTHORIZED)` responde `401` JSON en lugar de redirigir
   a un login (no existe en una API).
8. **Validación y manejo de errores.** `@Valid` + `GlobalExceptionHandler` devuelven `400` con el
   detalle por campo; `HttpMessageNotReadableException` y type mismatch también son `400`.
9. **`DataInitializer` idempotente.** Crea roles y usuarios solo si faltan; arrancar varias veces no
   duplica datos ni pisa ediciones.
10. **`ddl-auto: update`** para el arranque (conveniencia de desarrollo; migraciones Flyway quedan como
    mejora futura).
11. **CORS con lista explícita** (`CORS_ORIGINS`), nunca `*`.
12. **Pruebas con H2** en memoria para no requerir PostgreSQL al correr `mvn test`.

---

## 4. Autor y colaboradores

- **Autor:** Jordan Quezada
- **Colaboradores:** Exequiel Pulgar, Fabian Valencia y Jordan Quezada
