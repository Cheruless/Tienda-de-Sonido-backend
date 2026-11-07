package cl.tiendasonido.cartservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Seguridad de este microservicio: <strong>resource server JWT</strong>.
 *
 * <p><strong>Qué resuelve:</strong> todo endpoint de {@code /api/cart/**}
 * exige un token JWT válido emitido por {@code auth-service}. Sin este
 * archivo, cualquier usuario anónimo podría leer y modificar el carrito de
 * cualquier persona solo sabiendo su correo.</p>
 *
 * <p><strong>Cómo se arma el pedido</strong> (este patrón se repite en todos
 * los microservicios con estado del proyecto):</p>
 * <ol>
 *   <li>{@code csrf().disable()} — CSRF ataca aplicaciones con
 *       <em>sesiones</em> en navegador. Aquí no hay sesión
 *       ({@code STATELESS}), así que el vector no existe; desactivarlo es
 *       correcto y evita errores con peticiones sin token CSRF.</li>
 *   <li>{@code cors(...)} — aplica la política de {@link CorsConfig}.</li>
 *   <li>{@code sessionManagement(STATELESS)} — no se guarda nada entre
 *       peticiones; cada petición se valida solo con el JWT. Sin sesión no
 *       hay estado que robar ni que caducar.</li>
 *   <li>{@code authorizeHttpRequests(anyRequest().authenticated())} —
 *       <em>regla por defecto segura</em>: todo está prohibido salvo que
 *       alguien se autentique. Es mejor que listar rutas permitidas, porque
 *       una ruta nueva nace protegida sin acordarse nadie de agregarla.</li>
 *   <li>{@code oauth2ResourceServer(jwt)} — integra la validación de JWT de
 *       Spring Security: lee el header {@code Authorization: Bearer ...},
 *       verifica firma y expiración, y deja el objeto {@code Jwt} disponible
 *       en el contexto de seguridad.</li>
 *   <li>{@code authenticationEntryPoint(401)} — sin token, responde
 *       <strong>401 Unauthorized</strong> (y no un redirect a una página de
 *       login, que no existe en una API).</li>
 * </ol>
 *
 * <p><strong>Por qué un decoder propio ({@link #jwtDecoder(String)}):</strong>
 * el algoritmo de firma lo decide quien emite el token.
 * {@code auth-service} firma con <strong>HS256</strong> (algoritmo
 * simétrico: el mismo secreto firma y verifica), así que este servicio
 * necesita ese mismo secreto, llegado por la variable de entorno
 * {@code JWT_SECRET}. Si los secretos no coinciden, todos los tokens se
 * rechazan con 401.</p>
 *
 * <p><strong>Por qué no se valida el issuer:</strong> un validador de issuer
 * exige que el token traiga un claim {@code iss} con un valor esperado.
 * {@code auth-service} hoy no emite {@code iss}, así que validar sería
 * rechazar todos los tokens. Se documenta como deuda: cuando auth agregue el
 * claim, aquí debe sumarse un {@code JwtIssuerValidator}.</p>
 *
 * <p><strong>Por qué no se usa RS256 (asimétrico) todavía:</strong> RS256
 * resuelve el problema de distribuir claves públicas/privadas entre varios
 * servicios en máquinas distintas. En este proyecto todo corre en una sola
 * instancia EC2 con un secreto compartido, así que HS256 es el esquema más
 * simple <em>y</em> correcto. Es un trade-off deliberado, no un descuido.</p>
 *
 * <p><strong>Quién usa qué:</strong> {@code auth-service} <em>emite</em> el
 * token; este servicio solo lo <em>verifica</em>. La identidad del usuario
 * llega como claim {@code sub} (el correo acordado con auth) y la lee el
 * controlador con {@code @AuthenticationPrincipal}.</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see CorsConfig
 * @see cl.tiendasonido.cartservice.web.CartController
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Cadena de filtros de seguridad: define <em>qué</em> se protege y
     * <em>cómo</em> se autentica.
     *
     * <p>Orden de la cadena (los filtros se ejecutan en este orden): CORS
     * decide si la petición siquiera pasa; CSRF queda anulado; el
     * resource-server valida el JWT; si algo falla, el entry point responde
     * 401.</p>
     *
     * @param http configuración de Spring Security que se está construyendo
     * @return la cadena compilada lista para registrar en el contexto
     * @throws Exception si la configuración es inválida
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        )
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    /**
     * Crea el verificador de JWT: la pieza que dice "este token es auténtico".
     *
     * <p>Construye un decodificador HS256 con una clave derivada del secreto
     * compartido. Spring Security, al detectar este bean, lo usa
     * automáticamente en {@code oauth2ResourceServer(jwt(...))}. El decodificador
     * verifica firma y tiempo de expiración; si falla, la petición termina en
     * 401 antes de llegar al controlador.</p>
     *
     * <p><strong>Requisito del secreto:</strong> HS256 exige una clave de al
     * menos 256 bits (32 bytes). Si {@code JWT_SECRET} es más corta,
     * {@code NimbusJwtDecoder} falla al arrancar, no en cada petición.</p>
     *
     * @param secret secreto HS256 compartido con auth-service (env {@code JWT_SECRET});
     *               nunca se commitea al repositorio
     * @return verificador de tokens listo para usar
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
