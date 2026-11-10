package cl.tiendasonido.catalogservice.security;

import java.util.List;

public record DatosToken(String email, List<String> roles) {
}
