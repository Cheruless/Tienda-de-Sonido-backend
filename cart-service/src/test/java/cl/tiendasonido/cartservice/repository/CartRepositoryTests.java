package cl.tiendasonido.cartservice.repository;

import cl.tiendasonido.cartservice.domain.Cart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requiere Docker: levanta un MongoDB real en un contenedor temporal.
 */
@Testcontainers
@DataMongoTest
class CartRepositoryTests {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:8"));

    @Autowired
    private CartRepository repository;

    @Test
    void guardaYRecuperaPorUserId() {
        Cart cart = new Cart("user@example.com");
        cart.addOrMerge(new Cart.Item("demo-1", "Guitarra electrica", 199_990L, 2));
        repository.save(cart);

        Optional<Cart> encontrado = repository.findByUserId("user@example.com");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getItems()).hasSize(1);
        assertThat(encontrado.get().getItems().get(0).lineTotal()).isEqualTo(399_980L);
        assertThat(encontrado.get().total()).isEqualTo(399_980L);
    }

    @Test
    void laVersionAumentaEnCadaActualizacion() {
        Cart cart = repository.save(new Cart("version@example.com"));
        assertThat(cart.getVersion()).isZero();

        cart.addOrMerge(new Cart.Item("demo-1", "Guitarra electrica", 1_000L, 1));
        cart = repository.save(cart);
        assertThat(cart.getVersion()).isEqualTo(1);

        cart.addOrMerge(new Cart.Item("demo-2", "Bajo 4 cuerdas", 2_000L, 1));
        cart = repository.save(cart);
        assertThat(cart.getVersion()).isEqualTo(2);
    }
}
