# catalog-service

Microservicio del **catálogo** de Tienda Sonido. Gestiona productos y categorías, expone la consulta
pública del sitio y registra la venta con el **descuento de stock** de forma atómica.

- Puerto: `8082`
- Base de datos: MySQL 8 (`tienda_catalogo`), mapeada con Spring Data JPA
- Seguridad: filtro propio de JWT (Nimbus JOSE, HS256/384/512, mismo secreto que `auth-service`)
- Documentación interactiva: `/swagger-ui.html` y `/v3/api-docs` (abiertos)

---

## 1. Qué hace

1. **Consulta pública de productos** (`GET /api/productos`, `GET /api/productos/{id}`): lista con filtro
   opcional por `categoriaId` y obtiene un producto. No requiere token (es el catálogo del sitio).
2. **Consulta pública de categorías** (`GET /api/categorias`, `GET /api/categorias/{id}`).
3. **Administración (solo `ADMIN`)**:
   - Productos: `POST /api/productos`, `PUT /api/productos/{id}`, `DELETE /api/productos/{id}`.
   - Categorías: `POST /api/categorias`, `PUT /api/categorias/{id}`, `DELETE /api/categorias/{id}`.
4. **Venta** (`POST /api/productos/venta`): recibe un lote `{items:[{id, cantidad}]}` y descuenta el
   stock de todos los productos **en una sola transacción** (todo o nada). Lo llama `cart-service` en
   nombre del cliente autenticado: requiere token válido, pero **no** rol `ADMIN`.
5. **Datos iniciales**: al arrancar crea 10 categorías y 51 productos del plan de negocio, de forma
   idempotente (no sobreescribe lo editado por la API).

### Endpoints

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/productos?categoriaId=` | público | Lista productos (filtro opcional por categoría). |
| GET | `/api/productos/{id}` | público | Producto por id (`404` si no existe). |
| POST | `/api/productos` | `ADMIN` | Crea producto (`201`; `409` código duplicado, `404` categoría inexistente). |
| PUT | `/api/productos/{id}` | `ADMIN` | Actualiza producto. |
| DELETE | `/api/productos/{id}` | `ADMIN` | Elimina producto (`204`). |
| POST | `/api/productos/venta` | autenticado | Registra la venta y descuenta stock (`204`; `409` si falta stock, `404` producto inexistente). |
| GET | `/api/categorias` | público | Lista categorías. |
| GET | `/api/categorias/{id}` | público | Categoría por id. |
| POST | `/api/categorias` | `ADMIN` | Crea categoría (`201`; `409` nombre duplicado). |
| PUT | `/api/categorias/{id}` | `ADMIN` | Actualiza categoría. |
| DELETE | `/api/categorias/{id}` | `ADMIN` | Elimina si no tiene productos (`409` si los tiene). |

---

## 2. Modelo de flujo de operaciones simplificado

### Consulta pública

```
Cliente ──GET /api/productos?categoriaId=1──► ProductoController
                                                │ permitAll
                                                ▼
                                            ProductoService.listar(categoriaId)
                                                ▼
                                            ProductoRepository ──► MySQL (producto)
                                                │
                                                └─ 200 [ProductoResponse...]
```

### Administración (ADMIN)

```
Cliente ──POST /api/productos──► ProductoController
        {codigo, nombre, precio, stock, categoriaId...}   │ hasRole("ADMIN")
                                                          ▼
                                                     ProductoService.crear
                                                          │ ¿código ya existe (ignore case)? → 409
                                                          │ ¿categoría existe? → 404
                                                          ▼
                                                     ProductoRepository.save ──► MySQL
                                                          │
                                                          └─ 201 ProductoResponse
```

### Venta (descuento de stock)

```
cart-service ──POST /api/productos/venta──► ProductoController
        Authorization: Bearer <JWT>          │ authenticated (no requiere ADMIN)
        {items:[{id, cantidad}, ...]}        ▼
                                            ProductoService.vender  (@Transactional)
                                                │ por cada ítem:
                                                │   descontarStock(id, cantidad)
                                                │   → UPDATE ... WHERE id=? AND stock>=?
                                                │   0 filas → 409 (revierte TODO)
                                                ▼
                                            204  ── o ──  409 "stock insuficiente"
```

---

## 3. Decisiones de diseño

1. **MySQL + JPA.** Productos y categorías son entidades relacionadas con restricciones únicas
   (código de producto, nombre de categoría); el modelo relacional encaja y simplifica las validaciones.
2. **Filtro de JWT propio con Nimbus JOSE.** En lugar del resource server de Spring, valida la firma
   HMAC (HS256/384/512) y la expiración, y deja `sub` (correo) y `roles` en el contexto de seguridad.
   Valida que el secreto tenga al menos 32 caracteres al arrancar.
3. **Lecturas públicas, escritura administrativa.** `GET /api/productos/**` y `GET /api/categorias/**`
   son `permitAll` (el catálogo es el sitio de venta); crear/editar/eliminar exige rol `ADMIN`.
4. **La venta no es de `ADMIN`.** `POST /api/productos/venta` requiere solo un token válido, porque la
   ejecuta `cart-service` en nombre de un cliente. En producción, ese endpoint sería solo de red interna.
5. **Descuento de stock atómico + transacción.** El repositorio ejecuta
   `UPDATE producto SET stock = stock - :cantidad WHERE id = :id AND stock >= :cantidad`. La base de
   datos resuelve la carrera: con dos ventas simultáneas, una afecta 0 filas. `vender()` corre en una
   sola transacción, por lo que un fallo revierte todo el lote: no hay descuentos parciales que deshacer.
6. **Venta como lote.** Se modela `{items:[...]}` para poder descontar todo en una transacción y
   responder `409` si algo no alcanza, en lugar de descontar parcialmente.
7. **Unicidad insensible a mayúsculas.** El código de producto y el nombre de categoría se validan con
   `IgnoreCase`; no se puede eliminar una categoría con productos asociados (`409`).
8. **Seed idempotente.** `DataInitializer` crea categorías por nombre normalizado y productos por código;
   respeta lo editado por la API y puede correr en cada arranque.
9. **Manejo de errores consistente.** `GlobalExceptionHandler` mapea `RecursoNoEncontradoException` →
   `404`, `ConflictoException` → `409`, `TokenInvalidoException` → `401`; `SecurityConfig` responde `401`
   y `403` en JSON (sin redirects).
10. **Stateless, CSRF off, CORS con lista explícita** (`CORS_ORIGINS`). `ddl-auto: update` para el
    arranque; migraciones Flyway quedan como mejora futura.
11. **Pruebas con H2** en memoria para no requerir MySQL al correr `mvn test`.

---

## 4. Autor y colaboradores

- **Autor:** Fabian Valencia
- **Colaboradores:** Exequiel Pulgar, Fabian Valencia y Jordan Quezada
