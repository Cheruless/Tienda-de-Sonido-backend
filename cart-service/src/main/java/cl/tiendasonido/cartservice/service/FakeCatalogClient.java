package cl.tiendasonido.cartservice.service;

import cl.tiendasonido.cartservice.service.exception.ProductNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Cliente de catalogo para desarrollo mientras catalog-service no existe.
 * Se activa con catalog.client=fake (valor por defecto).
 *
 * BÁSICAMENTE VIENE SIENDO UN MOCK, NADA DEL OTRO MUNDO.
 */
@Service
@ConditionalOnProperty(name = "catalog.client", havingValue = "fake", matchIfMissing = true)
public class FakeCatalogClient implements CatalogClient {

    private final Map<String, ProductSummary> productos = Map.of(
            "demo-1", new ProductSummary("demo-1", "Guitarra electrica", 199_990L),
            "demo-2", new ProductSummary("demo-2", "Bajo 4 cuerdas", 149_990L),
            "demo-3", new ProductSummary("demo-3", "Amplificador 50W", 89_990L));

    @Override
    public ProductSummary getProduct(String productId) {
        ProductSummary producto = productos.get(productId);
        if (producto == null) {
            throw new ProductNotFoundException(productId);
        }
        return producto;
    }
}
