package cl.tiendasonido.catalogservice.security;

import cl.tiendasonido.catalogservice.exception.TokenInvalidoException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int LONGITUD_MINIMA_SECRETO = 32;
    private static final Set<JWSAlgorithm> ALGORITMOS_PERMITIDOS =
            Set.of(JWSAlgorithm.HS256, JWSAlgorithm.HS384, JWSAlgorithm.HS512);

    private final byte[] secreto;

    public JwtService(@Value("${jwt.secret}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalArgumentException("El secreto JWT debe tener al menos 32 caracteres");
        }
        this.secreto = bytes;
    }

    public DatosToken validar(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!ALGORITMOS_PERMITIDOS.contains(jwt.getHeader().getAlgorithm())) {
                throw new TokenInvalidoException("El algoritmo de firma no está permitido");
            }
            if (!jwt.verify(new MACVerifier(secreto))) {
                throw new TokenInvalidoException("La firma del token no es válida");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expira = claims.getExpirationTime();
            if (expira == null || expira.before(new Date())) {
                throw new TokenInvalidoException("El token expiró");
            }
            List<String> roles = claims.getStringListClaim("roles");
            return new DatosToken(claims.getSubject(), roles == null ? List.of() : roles);
        } catch (ParseException | JOSEException e) {
            throw new TokenInvalidoException("El token no es válido");
        }
    }
}
