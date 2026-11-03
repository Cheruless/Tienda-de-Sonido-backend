package cl.tiendasonido.cartservice.service;

import cl.tiendasonido.cartservice.domain.Cart;
import cl.tiendasonido.cartservice.repository.CartRepository;
import cl.tiendasonido.cartservice.service.exception.CartItemNotFoundException;
import cl.tiendasonido.cartservice.service.exception.ConflictException;
import cl.tiendasonido.cartservice.web.dto.AddItemRequest;
import cl.tiendasonido.cartservice.web.dto.CartItemResponse;
import cl.tiendasonido.cartservice.web.dto.CartResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CartService {

    /** Reintentos ante conflictos de concurrencia (optimistic locking). */
    private static final int MAX_INTENTOS = 3;

    private final CartRepository repository;
    private final CatalogClient catalogClient;

    public CartService(CartRepository repository, CatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    /** Lectura sin efectos secundarios: si no hay carrito, responde uno vacio. */
    public CartResponse getCart(String userId) {
        return repository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new CartResponse(List.of(), 0L));
    }

    public CartResponse addItem(String userId, AddItemRequest request) {
        // La consulta a catalogo queda fuera del bucle: no tiene sentido pagarla dos veces.
        ProductSummary product = catalogClient.getProduct(request.productId());

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                Cart cart = getOrCreate(userId);
                cart.addOrMerge(new Cart.Item(
                        product.id(), product.name(), product.price(), request.quantity()));
                return toResponse(persist(cart));
            } catch (OptimisticLockingFailureException ex) {
                // Otro escritor gano: releer y reintentar con la version nueva.
            }
        }
        throw new ConflictException("El carrito cambio por otra peticion, vuelve a intentarlo");
    }

    public void removeItem(String userId, String productId) {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            Cart cart = repository.findByUserId(userId)
                    .orElseThrow(() -> new CartItemNotFoundException(productId));
            if (!cart.remove(productId)) {
                throw new CartItemNotFoundException(productId);
            }
            try {
                persist(cart);
                return;
            } catch (OptimisticLockingFailureException ex) {
                // Releer y reintentar.
            }
        }
        throw new ConflictException("El carrito cambio por otra peticion, vuelve a intentarlo");
    }

    private Cart getOrCreate(String userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            try {
                return repository.save(new Cart(userId));
            } catch (DuplicateKeyException ex) {
                // Carrera de creacion: el indice unico gano; cargar el carrito existente.
                return repository.findByUserId(userId).orElseThrow();
            }
        });
    }

    private Cart persist(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        return repository.save(cart);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.lineTotal()))
                .toList();
        return new CartResponse(items, cart.total());
    }
}
