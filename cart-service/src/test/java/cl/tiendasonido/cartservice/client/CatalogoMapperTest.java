package cl.tiendasonido.cartservice.client;

import cl.tiendasonido.cartservice.service.ProductSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoMapperTest {

    private final CatalogoMapper mapper = new CatalogoMapper();

    @Test
    void mapeaNombrePrecioEIdDelCatalogo() {
        CatalogoProductoDto dto = new CatalogoProductoDto(7L, "Guitarra eléctrica", 199_990, 5);

        ProductSummary resumen = mapper.toProductSummary(dto);

        assertThat(resumen.id()).isEqualTo("7");
        assertThat(resumen.name()).isEqualTo("Guitarra eléctrica");
        assertThat(resumen.price()).isEqualTo(199_990L);
    }
}
