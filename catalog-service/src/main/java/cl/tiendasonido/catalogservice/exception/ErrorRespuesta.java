package cl.tiendasonido.catalogservice.exception;

import java.util.Map;

public record ErrorRespuesta(int estado, String mensaje, Map<String, String> detalles) {
}
