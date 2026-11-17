package cl.tiendasonido.cartservice.service;

import cl.tiendasonido.cartservice.domain.Cart;
import cl.tiendasonido.cartservice.repository.CartRepository;
import cl.tiendasonido.cartservice.service.exception.CartItemNotFoundException;
import cl.tiendasonido.cartservice.service.exception.CatalogUnavailableException;
import cl.tiendasonido.cartservice.service.exception.ConflictException;
import cl.tiendasonido.cartservice.web.dto.AddItemRequest;
import cl.tiendasonido.cartservice.web.dto.CartResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    @Mock
    private CartRepository repository;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private CartService service;

    @Test
    void agregaItemNuevo() {
        when(catalogClient.getProduct("demo-1"))
                .thenReturn(new ProductSummary("demo-1", "Guitarra electrica", 199_990L));
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = service.addItem("user@example.com",
                new AddItemRequest("demo-1", 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo("demo-1");
        assertThat(response.total()).isEqualTo(399_980L);
        // Un save para crear el carrito y otro para guardar el item.
        verify(repository, times(2)).save(any(Cart.class));
    }

    @Test
    void fusionaCantidadSiElProductoYaExiste() {
        Cart cart = new Cart("user@example.com");
        cart.setVersion(4L);
        cart.addOrMerge(new Cart.Item("demo-1", "Guitarra electrica", 199_990L, 1));

        when(catalogClient.getProduct("demo-1"))
                .thenReturn(new ProductSummary("demo-1", "Guitarra electrica", 199_990L));
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.of(cart));
        when(repository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = service.addItem("user@example.com",
                new AddItemRequest("demo-1", 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.total()).isEqualTo(599_970L);
        verify(repository, times(1)).save(any(Cart.class));
    }

    @Test
    void reintentaCuandoHayConflictoOptimista() {
        when(catalogClient.getProduct("demo-1"))
                .thenReturn(new ProductSummary("demo-1", "Guitarra electrica", 199_990L));
        // Cada intento carga un carrito nuevo, como ocurriria con la BD real.
        when(repository.findByUserId("user@example.com"))
                .thenAnswer(inv -> Optional.of(new Cart("user@example.com")));
        when(repository.save(any(Cart.class)))
                .thenThrow(new OptimisticLockingFailureException("conflicto"))
                .thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = service.addItem("user@example.com",
                new AddItemRequest("demo-1", 1));

        assertThat(response.items()).hasSize(1);
        verify(repository, times(2)).save(any(Cart.class));
    }

    @Test
    void lanzaConflictCuandoSeAgotanLosIntentos() {
        when(catalogClient.getProduct("demo-1"))
                .thenReturn(new ProductSummary("demo-1", "Guitarra electrica", 199_990L));
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(Cart.class)))
                .thenThrow(new OptimisticLockingFailureException("conflicto"));

        assertThatThrownBy(() -> service.addItem("user@example.com",
                new AddItemRequest("demo-1", 1)))
                .isInstanceOf(ConflictException.class);

        verify(repository, times(3)).save(any(Cart.class));
    }

    @Test
    void removerItemInexistenteLanzaError() {
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem("user@example.com", "demo-1"))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    // ---------- Compra (descuento de stock en catálogo) ----------

    @Test
    void comprarDescuentaStockYVaciaElCarrito() {
        Cart cart = new Cart("user@example.com");
        cart.addOrMerge(new Cart.Item("32", "Pedal Distorsión", 79_990L, 2));
        cart.addOrMerge(new Cart.Item("5", "Guitarra Acústica Folk", 129_990L, 1));
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.of(cart));

        service.comprar("user@example.com");

        // La venta se envía COMPLETA a catálogo (todo o nada).
        verify(catalogClient).registrarVenta(List.of(
                new ItemVenta("32", 2),
                new ItemVenta("5", 1)));
        verify(repository).delete(cart);
    }

    @Test
    void comprarNoVaciaElCarritoSiFaltaStock() {
        Cart cart = new Cart("user@example.com");
        cart.addOrMerge(new Cart.Item("32", "Pedal Distorsión", 79_990L, 2));
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.of(cart));
        doThrow(new ConflictException("Stock insuficiente para \"Pedal Distorsión\""))
                .when(catalogClient).registrarVenta(any());

        assertThatThrownBy(() -> service.comprar("user@example.com"))
                .isInstanceOf(ConflictException.class);

        // El carrito queda INTACTO: el usuario puede ajustar cantidades.
        verify(repository, never()).delete(any());
    }

    @Test
    void comprarConCarritoVacioLanzaError() {
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.comprar("user@example.com"))
                .isInstanceOf(CartItemNotFoundException.class);

        verify(catalogClient, never()).registrarVenta(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void comprarNoVaciaElCarritoSiCatalogoNoResponde() {
        Cart cart = new Cart("user@example.com");
        cart.addOrMerge(new Cart.Item("32", "Pedal Distorsión", 79_990L, 1));
        when(repository.findByUserId("user@example.com")).thenReturn(Optional.of(cart));
        doThrow(new CatalogUnavailableException("sin catálogo", new RuntimeException()))
                .when(catalogClient).registrarVenta(any());

        assertThatThrownBy(() -> service.comprar("user@example.com"))
                .isInstanceOf(CatalogUnavailableException.class);

        verify(repository, never()).delete(any());
    }
}
