package cl.tiendasonido.catalogservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.tiendasonido.catalogservice.exception.TokenInvalidoException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRETO = "cambia-este-secreto-por-uno-de-minimo-32-caracteres-seguro";
    private static final String OTRO_SECRETO = "otro-secreto-totalmente-distinto-con-mas-de-cuarenta-y-ocho-caracteres";

    private JwtService jwtService;

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(SECRETO);
    }

    private String crearToken(String secreto, JWSAlgorithm algoritmo, long milisegundosDeVida, List<String> roles)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("admin@sonidovivo.cl")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + milisegundosDeVida));
        if (roles != null) {
            claims.claim("roles", roles);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(algoritmo), claims.build());
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void aceptaUnTokenHS384FirmadoConElSecretoCompartido() throws Exception {
        String token = crearToken(SECRETO, JWSAlgorithm.HS384, 60_000, List.of("ROLE_ADMIN"));

        DatosToken datos = jwtService.validar(token);

        assertEquals("admin@sonidovivo.cl", datos.email());
        assertEquals(List.of("ROLE_ADMIN"), datos.roles());
    }

    @Test
    void aceptaUnTokenHS256() throws Exception {
        String token = crearToken(SECRETO, JWSAlgorithm.HS256, 60_000, List.of("ROLE_CLIENTE"));

        assertEquals(List.of("ROLE_CLIENTE"), jwtService.validar(token).roles());
    }

    @Test
    void unTokenSinRolesDevuelveUnaListaVacia() throws Exception {
        String token = crearToken(SECRETO, JWSAlgorithm.HS384, 60_000, null);

        assertTrue(jwtService.validar(token).roles().isEmpty());
    }

    @Test
    void rechazaUnTokenExpirado() throws Exception {
        String token = crearToken(SECRETO, JWSAlgorithm.HS384, -60_000, List.of("ROLE_ADMIN"));

        assertThrows(TokenInvalidoException.class, () -> jwtService.validar(token));
    }

    @Test
    void rechazaUnTokenFirmadoConOtroSecreto() throws Exception {
        String token = crearToken(OTRO_SECRETO, JWSAlgorithm.HS384, 60_000, List.of("ROLE_ADMIN"));

        assertThrows(TokenInvalidoException.class, () -> jwtService.validar(token));
    }

    @Test
    void rechazaTextoQueNoEsUnToken() {
        assertThrows(TokenInvalidoException.class, () -> jwtService.validar("esto-no-es-un-token"));
    }

    @Test
    void rechazaUnSecretoDemasiadoCorto() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("corto"));
    }
}
