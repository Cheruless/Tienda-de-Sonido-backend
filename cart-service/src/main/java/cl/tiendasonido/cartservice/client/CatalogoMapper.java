package cl.tiendasonido.cartservice.client;

import cl.tiendasonido.cartservice.service.ProductSummary;
import org.springframework.stereotype.Component;

/**
 * Traduce el contrato externo de catalog-service al contrato interno del carrito.
 *
 * <p>catalog-service devuelve {@code id} (Long), {@code nombre} (String) y
 * {@code precio} (Integer en CLP). El carrito trabaja con {@code id} (String),
 * {@code name} y {@code price} (Long). Este mapper es la única pieza que conoce
 * ambos contratos: si catálogo cambia nombres de campo o tipos, solo se toca aquí.</p>
 */
@Component
public class CatalogoMapper {

    /**
     * Convierte el DTO de catálogo en el resumen que consume el carrito.
     *
     * @param dto producto tal como llega de catalog-service
     * @return resumen con id como String, nombre y precio en CLP (Long)
     */
    public ProductSummary toProductSummary(CatalogoProductoDto dto) {
        return new ProductSummary(
                String.valueOf(dto.id()),
                dto.nombre(),
                dto.precio() == null ? null : dto.precio().longValue());
    }
}
