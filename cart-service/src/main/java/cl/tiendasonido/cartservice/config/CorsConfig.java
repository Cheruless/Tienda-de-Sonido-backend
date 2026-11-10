package cl.tiendasonido.cartservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configura <strong>CORS</strong> (Cross-Origin Resource Sharing) para este
 * microservicio.
 *
 * <p><strong>Qué es:</strong> el navegador bloquea por defecto que una
 * página (por ejemplo tu frontend en {@code http://localhost:5173}) haga
 * peticiones a otro origen (este servicio en {@code http://localhost:8083}).
 * Esa política se llama <em>same-origin policy</em> y protege al usuario:
 * sin ella, cualquier sitio podría leer datos de otros sitios usando tu
 * sesión. CORS es el mecanismo <em>legítimo</em> por el cual el servidor de
 * destino autoriza explícitamente a otros orígenes a consumirlo.</p>
 *
 * <p><strong>Cómo funciona:</strong> el navegador manda una petición
 * preflight {@code OPTIONS} antes de las peticiones que no son simples; si
 * este servicio responde con los encabezados {@code Access-Control-Allow-*}
 * correctos, el navegador deja pasar la petición real. Este bean provee esa
 * política para todas las rutas ({@code /**}).</p>
 *
 * <p><strong>Cuándo aplicarlo:</strong> siempre que un frontend corra en un
 * origen distinto al del API (puerto o dominio diferente). Si todo se sirve
 * desde el mismo origen vía el gateway, no haría falta; en este proyecto sí
 * se necesita porque el frontend corre en su propio puerto.</p>
 *
 * <p><strong>Lo importante en seguridad:</strong> se usan
 * {@code setAllowedOrigins} (lista explícita) y <em>no</em>
 * {@code setAllowedOriginPatterns("*")}. Abrir con {@code "*"} junto a
 * credenciales es la configuración insegura clásica: cualquier sitio podría
 * llamar al API. La lista viene de la propiedad {@code cors.allowed-origins}
 * (variable de entorno {@code CORS_ORIGINS}), así que cada entorno decide a
 * quién deja entrar.</p>
 *
 * <p><strong>No confundir con autenticación:</strong> CORS no autentica a
 * nadie. Solo dice <em>qué orígenes</em> pueden hacer peticiones; quién está
 * autenticado lo decide {@link SecurityConfig} con el JWT.</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see SecurityConfig
 */
@Configuration
public class CorsConfig {

    /**
     * Declara la política CORS que aplicará Spring Security a todas las rutas.
     *
     * <p>Detalles de la política:</p>
     * <ul>
     *   <li><strong>Orígenes:</strong> solo los listados en
     *       {@code cors.allowed-origins} (frontends de desarrollo).</li>
     *   <li><strong>Métodos:</strong> los verbos CRUD que usa la API.</li>
     *   <li><strong>Headers:</strong> se permiten {@code Authorization}
     *       (el JWT) y {@code Content-Type}; el resto se descarta.</li>
     *   <li><strong>MaxAge:</strong> 3600 s, cuánto el navegador cachea el
     *       resultado del preflight para no repetirlo en cada petición.</li>
     * </ul>
     *
     * @param allowedOrigins orígenes permitidos, desde la propiedad
     *                       {@code cors.allowed-origins} (env {@code CORS_ORIGINS})
     * @return la fuente de configuración CORS que Spring Security usará
     *         (este bean tiene que llamarse exactamente
     *         {@code corsConfigurationSource} para que {@code .cors(Customizer.withDefaults())}
     *         lo encuentre automáticamente)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}