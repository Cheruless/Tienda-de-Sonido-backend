# api-gateway

Microservicio de **entrada** de Tienda Sonido. Es el único punto público del backend: recibe las
peticiones del frontend en el puerto `8080` y las enruta hacia `auth-service`, `catalog-service` o
`cart-service` según el path.

- Puerto: `8080`
- Sin base de datos y sin estado
- Spring Cloud Gateway (`spring-cloud-starter-gateway-server-webmvc`, stack WebMVC)

---

## 1. Qué hace

1. **Enruta por path** hacia el servicio correcto:

   | Path | Destino |
   |---|---|
   | `/api/auth/**`, `/api/users/**` | `auth-service` (`AUTH_SERVICE_URL`, por defecto `http://localhost:8081`) |
   | `/api/productos/**`, `/api/categorias/**` | `catalog-service` (`CATALOG_SERVICE_URL`, por defecto `http://localhost:8082`) |
   | `/api/cart/**` | `cart-service` (`CART_SERVICE_URL`, por defecto `http://localhost:8083`) |

2. **Reenvía el header `Authorization`** sin tocarlo, para que cada servicio valide el JWT del usuario.
3. **Elimina el header `Origin`** antes del reenvío (`RemoveRequestHeader=Origin`), de modo que el
   servicio destino no reciba el `Origin` del gateway.
4. **Aplica CORS** centralizado con la lista explícita de orígenes (`CORS_ORIGINS`).
5. **Responde `404`** para cualquier ruta no definida.

El gateway **no valida tokens ni tiene seguridad propia**: la autenticación la aplica cada microservicio,
de modo que una petición directa por la red interna también queda protegida.

---

## 2. Modelo de flujo de operaciones simplificado

```
Frontend ──HTTP :8080──► api-gateway
                            │
                            │ 1) CORS: ¿origen permitido?
                            │ 2) RemoveRequestHeader=Origin
                            │ 3)¿el path coincide con una ruta?
                            │        no ──► 404
                            │        sí
                            ▼
                  reenvía con Authorization intacto
                            │
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                    ▼
  auth-service        catalog-service       cart-service
  (:8081)              (:8082)               (:8083)
```

Flujo de una petición de negocio:

```
POST /api/cart/items  (Authorization: Bearer <JWT>)
   │
   ▼
api-gateway  ── reenvía Authorization y sin Origin ──►  cart-service
                                                          │ valida el JWT
                                                          ▼
                                                     201 / 401 / 404 / 409
```

---

## 3. Decisiones de diseño

1. **Un solo punto de entrada.** El frontend solo conoce el puerto `8080`; los servicios no se exponen
   directamente al navegador.
2. **Spring Cloud Gateway.** Enrutamiento declarado en `application.yaml` (predicados `Path` por
   servicio), sin código de negocio.
3. **El gateway no valida el token.** Reenvía `Authorization` y cada servicio aplica su propia
   seguridad. Es defensa en profundidad: si alguien entra directo por la red de Docker, sigue protegido.
4. **`RemoveRequestHeader=Origin`.** El gateway es quien responde al navegador con CORS; quitar `Origin`
   antes de reenviar evita que el servicio destino intente aplicar su propia política CORS a una petición
   interna.
5. **CORS centralizado en el gateway** con lista explícita de orígenes (`CORS_ORIGINS`), nunca `*`. Los
   servicios también tienen su CORS por si se llaman directo (tests, depuración).
6. **URLs destino por variable de entorno.** En Compose se usan los nombres DNS
   (`auth-service:8081`, etc.); en local, `localhost`. No hay service discovery: los DNS del Compose
   bastan para una sola EC2.
7. **Stateless y sin BD.** El gateway no guarda estado ni datos: es una pieza de enrutamiento puro.
8. **`404` por defecto.** Una ruta no definida no se reenvía; falla rápido.
9. **Pruebas con stubs HTTP reales** (`HttpServer` de JDK): verifican enrutamiento, reenvío de
   `Authorization`, eliminación de `Origin` y `404` de rutas desconocidas.

---

## 4. Autor y colaboradores

- **Autores:** Exequiel Pulgar, Fabian Valencia y Jordan Quezada
- **Colaboradores:** Exequiel Pulgar, Fabian Valencia y Jordan Quezada
