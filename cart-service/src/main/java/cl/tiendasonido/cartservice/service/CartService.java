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

/**
 * Casos de uso del carrito: leer, agregar y quitar items.
 *
 * <p><strong>Patrón:</strong> la capa {@code service} orquesta el flujo y
 * delega la persistencia en {@link CartRepository} y la consulta de productos
 * en {@link CatalogClient}. El controlador solo traduce HTTP &rarr; servicio,
 * sin lógica de negocio.</p>
 *
 * <p><strong>Concurrencia (lo más delicado de esta clase):</strong> todas las
 * escrituras siguen el patrón <em>leer &rarr; modificar &rarr; guardar</em>,
 * que es propenso a <em>lost updates</em> si dos peticiones del mismo usuario
 * llegan a la vez. La defensa tiene tres piezas que funcionan juntas:</p>
 * <ol>
 *   <li>{@link Cart#version} ({@code @Version}) detecta el conflicto y lanza
 *       {@code OptimisticLockingFailureException};</li>
 *   <li>este servicio <strong>reintenta</strong> la lectura completa
 *       {@link #MAX_INTENTOS} veces antes de rendirse;</li>
 *   <li>si se agota, responde <strong>409 Conflict</strong> mediante
 *       {@link ConflictException} para que el cliente reintente, en vez de
 *       fallar con 500 o sobrescribir datos en silencio.</li>
 * </ol>
 *
 * <p>Un cuarto punto: crear carrito a la vez desde dos peticiones lo evita el
 * índice único de {@code userId}, que provoca {@code DuplicateKeyException}
 * (ver {@link #getOrCreate(String)}).</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see Cart
 * @see CartRepository
 */
@Service
public class CartService {

    /**
     * Reintentos ante conflictos de concurrencia (optimistic locking).
     * Tres es un buen balance: cubre el caso normal de dos pestañas sin
     * multiplicar la latencia si el conflicto persiste.
     */
    private static final int MAX_INTENTOS = 3;

    private final CartRepository repository;
    private final CatalogClient catalogClient;

    /**
     * Inyecta las dos dependencias del servicio: dónde persistir y dónde consultar.
     *
     * @param repository    acceso a la colección {@code carts} de MongoDB
     * @param catalogClient puerto para consultar productos a catálogo
     */
    public CartService(CartRepository repository, CatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    /**
     * Devuelve el carrito del usuario; si aún no existe, devuelve uno vacío
     * (no es un error: un usuario nuevo todavía no agregó nada).
     *
     * <p>Es una lectura sin efectos secundarios, así que no necesita reintentos.</p>
     *
     * @param userId correo electrónico del dueño (claim {@code sub} del JWT)
     * @return carrito con sus items y total, o carrito vacío
     */
    public CartResponse getCart(String userId) {
        return repository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new CartResponse(List.of(), 0L));
    }

    /**
     * Agrega un producto al carrito, fusionando cantidades si ya existe.
     *
     * <p>Flujo: primero consulta catálogo (si el producto no existe, lanza
     * {@link cl.tiendasonido.cartservice.service.exception.ProductNotFoundException}
     * &rarr; 404) y <strong>después</strong>
     * entra al bucle de reintentos. La consulta HTTP queda fuera del bucle a
     * propósito: un conflicto de concurrencia no requiere pagar la llamada a
     * catálogo otra vez.</p>
     *
     * @param userId  correo electrónico del dueño (claim {@code sub} del JWT)
     * @param request producto y cantidad a agregar (cantidad &ge; 1)
     * @return el carrito completo después de la operación
     * @throws cl.tiendasonido.cartservice.service.exception.ProductNotFoundException si catálogo no conoce el producto (&rarr; 404)
     * @throws ConflictException                                                      si tras {@value #MAX_INTENTOS} reintentos el carrito siguió cambiando (&rarr; 409)
     */
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
                // Otro escritor ganó: releer y reintentar con la version nueva.
            }
        }
        throw new ConflictException("El carrito cambio por otra peticion, vuelve a intentarlo");
    }

    /**
     * Quita del carrito la línea del producto indicado.
     *
     * <p>Mismo patrón de reintentos que {@link #addItem(String, AddItemRequest)}:
     * lee, modifica y guarda, y si otra peticion gano, vuelve a leer. Nota que
     * el producto debe estar en el carrito; si no, lanza
     * {@link CartItemNotFoundException} (&rarr; 404) sin reintentar, porque no
     * hay nada que guardar.</p>
     *
     * @param userId    correo electrónico del dueño (claim {@code sub} del JWT)
     * @param productId producto a eliminar del carrito
     * @throws CartItemNotFoundException si el producto no está en el carrito (&rarr; 404)
     * @throws ConflictException         si se agotaron los reintentos (&rarr; 409)
     */
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
        throw new ConflictException("El carrito cambió por otra petición, vuelve a intentarlo");
    }

    /**
     * Busca el carrito del usuario y, si no existe, lo crea.
     *
     * <p>La carrera clásica: dos peticiones simultáneas de un usuario nuevo
     * llegan a {@code findByUserId} a la vez, ambas ven "no existe" y ambas
     * intentan insertar. El índice único de {@code userId} deja pasar solo
     * una; la otra recibe {@code DuplicateKeyException} y aquí se recarga el
     * carrito que acaba de crear la primera, en lugar de fallar.</p>
     *
     * @param userId correo del dueño
     * @return el carrito existente o recién creado
     */
    private Cart getOrCreate(String userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            try {
                return repository.save(new Cart(userId));
            } catch (DuplicateKeyException ex) {
                // Carrera de creación: el índice único ganó; cargar el carrito existente.
                return repository.findByUserId(userId).orElseThrow();
            }
        });
    }

    /**
     * Guarda el carrito actualizando su marca de tiempo.
     *
     * <p>Es el único punto de escritura: si el documento cambio desde que se
     * leyó, Spring Data comparará la versión y lanzará
     * {@code OptimisticLockingFailureException}, que los métodos públicos
     * convierten en reintento.</p>
     *
     * @param cart carrito modificado en memoria
     * @return el carrito tal como quedó persistido
     */
    private Cart persist(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        return repository.save(cart);
    }

    /**
     * Traduce la entidad de dominio al DTO de respuesta, calculando los
     * totales en el momento (no se persisten).
     *
     * @param cart carrito leído de la BD
     * @return DTO listo para serializar a JSON
     */
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
