# cart-service

Microservicio del **carrito de compras** de Tienda Sonido. Mantiene un carrito por usuario en
MongoDB, agrega/quita productos con un snapshot de nombre y precio de catálogo, y ejecuta la compra
coordinando el descuento de stock con `catalog-service`.

- Puerto: `8083`
- Base de datos: MongoDB 8 (`carts`), mapeada con Spring Data MongoDB
- Seguridad: resource server JWT (HS256, mismo secreto que `auth-service`)
- Documentación interactiva: `/swagger-ui.html` y `/v3/api-docs` (abiertos)

---

## 1. Qué hace

1. **Ver el carrito** (`GET /api/cart`): devuelve los ítems y el total del usuario autenticado. Si aún
   no existe, responde `200` con un carrito vacío (no es un error).
2. **Agregar producto** (`POST /api/cart/items`): recibe `productId` y `quantity`, consulta el producto
   en catálogo y copia `productName`/`unitPrice` como snapshot. Si el producto ya está, suma cantidades.
3. **Quitar producto** (`DELETE /api/cart/items/{productId}`): elimina la línea del carrito (`204`,
   `404` si no estaba).
4. **Comprar** (`POST /api/cart/compra`): envía el carrito como lote a catálogo (`POST
   /api/productos/venta`, reenviando el JWT del usuario) y, solo si catálogo confirma, vacía el carrito.

La identidad del usuario **nunca** viene en la URL ni en el body: se toma del claim `sub` del JWT
(el correo), validado por Spring Security.

### Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/cart` | Carrito del usuario: `{items:[...], total}`. `401` sin token. |
| POST | `/api/cart/items` | Agrega `{productId, quantity}` y devuelve el carrito actualizado (`201`). `400` validación, `404` producto inexistente, `409` conflicto de concurrencia. |
| DELETE | `/api/cart/items/{productId}` | Quita el ítem (`204`). `404` si no estaba. |
| POST | `/api/cart/compra` | Compra el carrito (`204`). `404` carrito vacío, `409` stock insuficiente, `503` catálogo caído. |

---

## 2. Modelo de flujo de operaciones simplificado

### Ver y modificar el carrito

```
Cliente ──GET /api/cart───────────────► CartController
                                          │ jwt.getSubject() = email
                                          ▼
                                      CartService.getCart(email)
                                          ▼
                                      CartRepository.findByUserId(email) ──► MongoDB
                                          │
                                          └─ 200 {items, total}   (vacío si no existe)

Cliente ──POST /api/cart/items───────► CartController
        {productId, quantity}          │ jwt.getSubject() = email
                                       ▼
                                   CatalogClient.getProduct(id) ──► catalog-service
                                       │ (snapshot nombre/precio)
                                       ▼
                                   bucle con reintento (máx. 3):
                                     leer carrito → addOrMerge → save (@Version)
                                       │ conflicto → releer y reintentar
                                       │ agotados → 409
                                       ▼
                                   201 {items, total}
```

### Compra

```
Cliente ──POST /api/cart/compra──────► CartService.comprar(email)
                                       │ 1) carrito vacío → 404
                                       ▼
                                   CatalogClient.registrarVenta(items)
                                       │ reenvía el JWT (token propagation)
                                       ▼
                                   catalog-service: descuenta stock
                                       │ UNA transacción, todo o nada
                                       │ 409 → no se tocó nada y el carrito queda intacto
                                       ▼
                                   2) repository.delete(cart)
                                       ▼
                                   204
```

---

## 3. Decisiones de diseño

1. **MongoDB: un documento por carrito con ítems embebidos.** El carrito es un agregado; no hay joins
   ni consultas entre usuarios, así que embeber los ítems hace cada operación atómica sobre un documento.
2. **Concurrencia con optimistic locking.** Tres piezas que funcionan juntas:
   - `@Version Long version` detecta que otro escritor modificó el documento;
   - `CartService` reintenta el read-modify-write hasta **3 veces**;
   - si se agota, responde `409 Conflict` (nunca `500` ni escritura silenciosa).
   - `@Indexed(unique = true)` en `userId` evita dos carritos por usuario; la carrera de creación se
     resuelve con `DuplicateKeyException` recargando el carrito ganador.
3. **Dinero como `Long` (CLP entero).** `unitPrice`, `lineTotal` y `total` son enteros; nunca `double`.
   Los totales se calculan al responder, no se persisten (sin valores desactualizados).
4. **Snapshot de catálogo.** `productName` y `unitPrice` se copian al agregar; si catálogo cambia el
   precio después, el carrito conserva lo que el usuario vio. Es intencional.
5. **El cliente no envía el precio.** `AddItemRequest` solo trae `productId` y `quantity`; el precio lo
   pone catálogo, por lo que no se puede falsear.
6. **Puerto y adaptador (`CatalogClient`).** El servicio depende de una interfaz; hoy puede usarse
   `HttpCatalogClient` (`catalog.client=http`) o `FakeCatalogClient` (`catalog.client=fake`). Cambiar de
   implementación no toca la lógica de negocio.
7. **Token propagation.** La llamada interna a catálogo reenvía el `Authorization` del usuario, de modo
   que la venta queda registrada a nombre del cliente real.
8. **Orden de la compra.** Primero se reserva el stock (recurso escaso) y recién al confirmar se vacía el
   carrito. Si el vaciado fallara, lo peor es que el usuario tenga que vaciarlo a mano: jamás se paga sin
   stock.
9. **Seguridad: resource server.** Todo está protegido (`anyRequest().authenticated()`), stateless, CSRF
   off, `401` JSON sin redirect. El `JwtDecoder` valida HS256 con `JWT_SECRET`; no valida `iss` porque
   `auth-service` no lo emite (deuda documentada).
10. **Identidad desde el token.** `@AuthenticationPrincipal Jwt` → `getSubject()`; nadie puede leer el
    carrito de otro cambiando un identificador en la URL.
11. **CORS con lista explícita** (`CORS_ORIGINS`); Swagger queda abierto para documentación.
12. **Pruebas:** unitarias con Mockito, web con MockMvc + JWT, y repositorio con Testcontainers
    (requiere Docker).

---

## 4. Autor y colaboradores

- **Autor:** Exequiel Pulgar
- **Colaboradores:** Exequiel Pulgar, Fabian Valencia y Jordan Quezada
