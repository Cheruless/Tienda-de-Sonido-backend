package cl.tiendasonido.cartservice.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddItemRequest(
        @NotBlank(message = "El producto es obligatorio") String productId,

        @Min(value = 1, message = "La cantidad debe ser al menos 1") int quantity) {
}
