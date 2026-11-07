package cl.tiendasonido.catalogservice.config;

import cl.tiendasonido.catalogservice.security.JwtAuthenticationFilter;
import cl.tiendasonido.catalogservice.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        .requestMatchers("/api/v1/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((request, response, ex) ->
                                responderJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Debes iniciar sesión con un token válido"))
                        .accessDeniedHandler((request, response, ex) ->
                                responderJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        "No tienes permisos para realizar esta acción")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void responderJson(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"estado\":" + estado + ",\"mensaje\":\"" + mensaje + "\"}");
    }
}
