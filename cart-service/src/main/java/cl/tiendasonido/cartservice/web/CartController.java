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

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getCart(jwt.getSubject());
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody AddItemRequest request) {
        CartResponse response = cartService.addItem(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable String productId) {
        cartService.removeItem(jwt.getSubject(), productId);
        return ResponseEntity.noContent().build();
    }
}
