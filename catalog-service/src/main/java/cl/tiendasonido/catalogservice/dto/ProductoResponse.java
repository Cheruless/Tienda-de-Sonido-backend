package cl.tiendasonido.catalogservice.dto;

import cl.tiendasonido.catalogservice.entity.Categoria;
import cl.tiendasonido.catalogservice.entity.Producto;

public record ProductoResponse(
        Long id,
        String codigo,
        String nombre,
        String marca,
        String modelo,
        String descripcion,
        Integer precio,
        Integer stock,
        Long categoriaId,
        String categoria) {

    public static ProductoResponse from(Producto producto) {
        Categoria categoria = producto.getCategoria();
        return new ProductoResponse(
                producto.getId(),
                producto.getCodigo(),
                producto.getNombre(),
                producto.getMarca(),
                producto.getModelo(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getStock(),
                categoria.getId(),
                categoria.getNombre());
    }
}
