package cl.tiendasonido.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de rutas del gateway contra stubs HTTP reales.
 *
 * <p>Levanta tres servidores mínimos (auth, catalog, cart) que responden un JSON
 * indicando qué servicio recibió la petición. Así se verifica que el gateway
 * enruta cada path al servicio correcto, que reenvía el header {@code Authorization}
 * y que elimina el header {@code Origin} (por el filtro RemoveRequestHeader).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTest {

    @LocalServerPort
    private int gatewayPort;

    private final RestClient http = RestClient.create();

    private static HttpServer authStub;
    private static HttpServer catalogStub;
    private static HttpServer cartStub;

    @DynamicPropertySource
    static void stubs(DynamicPropertyRegistry registry) throws IOException {
        authStub = startStub("auth");
        catalogStub = startStub("catalog");
        cartStub = startStub("cart");

        registry.add("AUTH_SERVICE_URL", () -> "http://localhost:" + authStub.getAddress().getPort());
        registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + catalogStub.getAddress().getPort());
        registry.add("CART_SERVICE_URL", () -> "http://localhost:" + cartStub.getAddress().getPort());
    }

    @AfterAll
    static void detenerStubs() {
        authStub.stop(0);
        catalogStub.stop(0);
        cartStub.stop(0);
    }

    private static HttpServer startStub(String service) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String origin = exchange.getRequestHeaders().getFirst(HttpHeaders.ORIGIN);
            String body = "{\"service\":\"" + service
                    + "\",\"authorization\":\"" + (authorization == null ? "" : authorization)
                    + "\",\"origin\":\"" + (origin == null ? "" : origin) + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String url(String path) {
        return "http://localhost:" + gatewayPort + path;
    }

    private void aplicarCabeceras(HttpHeaders headers, String authorization, String origin) {
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (origin != null) {
            headers.set(HttpHeaders.ORIGIN, origin);
        }
    }

    private ResponseEntity<String> get(String path, String authorization, String origin) {
        try {
            return http.get().uri(url(path))
                    .headers(headers -> aplicarCabeceras(headers, authorization, origin))
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return new ResponseEntity<>(ex.getResponseBodyAsString(), ex.getStatusCode());
        }
    }

    private ResponseEntity<String> post(String path, String body, String authorization, String origin) {
        try {
            return http.post().uri(url(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> aplicarCabeceras(headers, authorization, origin))
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException.NotFound ex) {
            return new ResponseEntity<>(ex.getResponseBodyAsString(), ex.getStatusCode());
        }
    }

    @Test
    void enrutaLoginDeAuth() {
        ResponseEntity<String> response = post("/api/auth/login",
                "{\"email\":\"a@b.cl\",\"password\":\"x\"}", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"auth\"");
    }

    @Test
    void enrutaUsersMeDeAuthYReenviaAuthorization() {
        ResponseEntity<String> response = get("/api/users/me", "Bearer token-auth", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"auth\"");
        assertThat(response.getBody()).contains("\"authorization\":\"Bearer token-auth\"");
    }

    @Test
    void enrutaProductosDeCatalog() {
        ResponseEntity<String> response = get("/api/productos", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"catalog\"");
    }

    @Test
    void enrutaCategoriasDeCatalog() {
        ResponseEntity<String> response = get("/api/categorias", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"catalog\"");
    }

    @Test
    void enrutaItemsDeCartYReenviaAuthorization() {
        ResponseEntity<String> response = post("/api/cart/items",
                "{\"productId\":\"1\",\"quantity\":2}", "Bearer token-cart", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"cart\"");
        assertThat(response.getBody()).contains("\"authorization\":\"Bearer token-cart\"");
    }

    @Test
    void elGatewayEliminaElHeaderOrigin() {
        ResponseEntity<String> response = get("/api/productos", null, "http://localhost:5173");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"origin\":\"\"");
    }

    @Test
    void rutaNoDefinidaDevuelve404() {
        ResponseEntity<String> response = get("/api/desconocido", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
