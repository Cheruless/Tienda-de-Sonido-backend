package cl.tiendasonido.catalogservice.security;

import cl.tiendasonido.catalogservice.exception.TokenInvalidoException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_CABECERA = "Bearer ";
    private static final String PREFIJO_ROL = "ROLE_";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO_CABECERA)) {
            try {
                DatosToken datos = jwtService.validar(cabecera.substring(PREFIJO_CABECERA.length()));
                List<GrantedAuthority> autoridades = datos.roles().stream()
                        .map(rol -> rol.startsWith(PREFIJO_ROL) ? rol : PREFIJO_ROL + rol)
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(datos.email(), null, autoridades));
            } catch (TokenInvalidoException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
