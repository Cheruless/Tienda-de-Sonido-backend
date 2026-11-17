package cl.tiendasonido.cartservice.client;

import cl.tiendasonido.cartservice.service.CatalogClient;
import cl.tiendasonido.cartservice.service.ItemVenta;
import cl.tiendasonido.cartservice.service.ProductSummary;
import cl.tiendasonido.cartservice.service.exception.CatalogUnavailableException;
import cl.tiendasonido.cartservice.service.exception.ConflictException;
import cl.tiendasonido.cartservice.service.exception.ProductInvalidException;
import cl.tiendasonido.cartservice.service.exception.ProductNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    /**
     * Registra la venta en catálogo reenviando el JWT del usuario autenticado.
     *
     * <p>El backend de catálogo valida ese token (mismo secreto HS256): la
     * compra queda hecha "a nombre" del cliente real, no de cart-service.
     * Es el patrón <em>token propagation</em> de microservicios.</p>
     */
    @Override
    public void registrarVenta(List<ItemVenta> items) {
        // catálogo espera {items: [{id, cantidad}]} — id numérico
        List<Map<String, Object>> cuerpo = items.stream()
                .map(i -> Map.<String, Object>of("id", Long.valueOf(i.productId()), "cantidad", i.cantidad()))
                .toList();
        try {
            restClient.post()
                    .uri("/api/productos/venta")
                    .headers(this::agregarJwtDelUsuario)
                    .body(Map.of("items", cuerpo))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict ex) {
            throw new ConflictException(extraerMensaje(ex));
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException("algún producto de la venta");
        } catch (HttpClientErrorException ex) {
            throw new CatalogUnavailableException(
                    "El catálogo rechazó la venta: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new CatalogUnavailableException(
                    "El catálogo respondió " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new CatalogUnavailableException("No se pudo conectar con el catálogo", ex);
        }
    }

    /**
     * Copia el header Authorization del usuario autenticado a la llamada
     * hacia catálogo (token propagation). Sin token en contexto (p.ej. en
     * tests unitarios) se omite.
     */
    private void agregarJwtDelUsuario(HttpHeaders headers) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            headers.setBearerAuth(jwt.getToken().getTokenValue());
        }
    }

    /** Extrae el mensaje de error del cuerpo que devuelve catálogo. */
    private String extraerMensaje(HttpClientErrorException ex) {
        try {
            var json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(ex.getResponseBodyAsString());
            return json.path("mensaje").asText("Stock insuficiente");
        } catch (Exception e) {
            return "Stock insuficiente";
        }
    }
}
