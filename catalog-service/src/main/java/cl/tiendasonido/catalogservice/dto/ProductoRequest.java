package cl.tiendasonido.catalogservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 20, message = "El código no puede superar los 20 caracteres")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 3, max = 80, message = "El nombre debe tener entre 3 y 80 caracteres")
        String nombre,

        @NotBlank(message = "La marca es obligatoria")
        @Size(max = 60, message = "La marca no puede superar los 60 caracteres")
        String marca,

        @Size(max = 60, message = "El modelo no puede superar los 60 caracteres")
        String modelo,

        @Size(max = 200, message = "La descripción no puede superar los 200 caracteres")
        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @Min(value = 1, message = "El precio debe ser mayor que 0")
        Integer precio,

        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock,

        @NotNull(message = "La categoría es obligatoria")
        Long categoriaId) {
}
