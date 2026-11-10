package cl.tiendasonido.cartservice.client;

import cl.tiendasonido.cartservice.service.CatalogClient;
import cl.tiendasonido.cartservice.service.ProductSummary;
import cl.tiendasonido.cartservice.service.exception.CatalogUnavailableException;
import cl.tiendasonido.cartservice.service.exception.ProductInvalidException;
import cl.tiendasonido.cartservice.service.exception.ProductNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Implementación HTTP de {@link CatalogClient} con {@link RestClient}.
 * Se activa con {@code catalog.client=http}; en development se usa
 * {@code FakeCatalogClient} ({@code catalog.client=fake}).
 */
@Component
@ConditionalOnProperty(name = "catalog.client", havingValue = "http")
public class HttpCatalogClient implements CatalogClient {

    private final RestClient restClient;
    private final CatalogoMapper mapper;

    public HttpCatalogClient(@Value("${catalog.base-url}") String baseUrl,
                             CatalogoMapper mapper) {
        // RestClient.builder() es la factory estatica de Spring Framework:
        // no depende de un bean auto-configurado por Boot.
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    @Override
    public ProductSummary getProduct(String productId) {
        CatalogoProductoDto dto;
        try {
            dto = restClient.get()
                    .uri("/api/productos/{id}", productId)
                    .retrieve()
                    .body(CatalogoProductoDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new ProductInvalidException(productId);
        } catch (HttpClientErrorException ex) {
            throw new CatalogUnavailableException(
                    "El catálogo respondió " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new CatalogUnavailableException(
                    "El catálogo respondió " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new CatalogUnavailableException("No se pudo conectar con el catálogo", ex);
        }

        if (dto == null || dto.id() == null || dto.nombre() == null) {
            throw new ProductNotFoundException(productId);
        }
        return mapper.toProductSummary(dto);
    }
}
