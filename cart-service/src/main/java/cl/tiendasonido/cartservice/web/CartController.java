package cl.tiendasonido.cartservice.web;

import cl.tiendasonido.cartservice.service.CartService;
import cl.tiendasonido.cartservice.web.dto.AddItemRequest;
import cl.tiendasonido.cartservice.web.dto.CartResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del carrito. Expone los casos de uso de {@link CartService}
 * traduciéndolos a HTTP.
 *
 * <p><strong>Convención de rutas:</strong> todo vive bajo {@code /api/cart}
 * (el prefijo {@code /api} se acuerda con el equipo y el gateway).</p>
 *
 * <p><strong>Identidad del usuario:</strong> nunca viene en la URL ni en el
 * body de la petición, sino del JWT. El parámetro
 * {@code @AuthenticationPrincipal Jwt jwt} recibe el token que el
 * resource-server ya validó en {@code SecurityConfig}, y
 * {@code jwt.getSubject()} extrae el claim {@code sub} (el correo del
 * usuario). Así nadie puede leer el carrito de otro solo cambiando un
 * identificador en la URL.</p>
 *
 * <p><strong>Qué NO hace:</strong> este controlador no contiene lógica de
 * negocio. Solo orquesta: delega en {@link CartService} y traduce el
 * resultado a código HTTP (200/201/204). Los errores se mapean a códigos de
 * estado en {@link cl.tiendasonido.cartservice.web.error.GlobalExceptionHandler}.</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see CartService
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    /**
     * Inyecta el servicio con los casos de uso del carrito.
     *
     * @param cartService casos de uso del carrito, inyectado por Spring
     */
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * {@code GET /api/cart} — devuelve el carrito del usuario autenticado.
     *
     * <p>Si el usuario nunca agregó nada, responde 200 con una lista vacía
     * y total 0 (no 404: pedir un carrito vacío no es un error).</p>
     *
     * @param jwt token ya validado; de ahí sale el correo del dueño
     * @return el carrito con items y total en CLP
     */
    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getCart(jwt.getSubject());
    }

    /**
     * {@code POST /api/cart/items} — agrega un producto al carrito.
     *
     * <p>El body trae {@code productId} y {@code quantity}; el precio y el
     * nombre los copia el servicio desde catálogo (snapshot), por lo que el
     * cliente no puede mandar un precio falso.</p>
     *
     * @param jwt     token ya validado
     * @param request producto y cantidad (validados con {@code @Valid})
     * @return 201 con el carrito completo actualizado; 400 si la validación
     *         falla; 404 si el producto no existe en catálogo; 409 si hubo
     *         conflictos de concurrencia; 401 sin token
     */
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody AddItemRequest request) {
        CartResponse response = cartService.addItem(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * {@code DELETE /api/cart/items/{productId}} — quita un producto del carrito.
     *
     * @param jwt       token ya validado
     * @param productId producto a eliminar
     * @return 204 sin cuerpo si se eliminó; 404 si el producto no estaba en
     *         el carrito o el carrito no existe; 409 si hubo conflictos
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable String productId) {
        cartService.removeItem(jwt.getSubject(), productId);
        return ResponseEntity.noContent().build();
    }
}
