package cl.tiendasonido.cartservice.web.dto;

import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        long total) {
}
