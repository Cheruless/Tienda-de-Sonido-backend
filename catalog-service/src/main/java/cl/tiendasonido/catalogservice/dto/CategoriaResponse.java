package cl.tiendasonido.catalogservice.dto;

import cl.tiendasonido.catalogservice.entity.Categoria;

public record CategoriaResponse(Long id, String nombre) {

    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre());
    }
}
