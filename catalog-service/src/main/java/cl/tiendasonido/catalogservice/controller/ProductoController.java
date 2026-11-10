package cl.tiendasonido.catalogservice.controller;

import cl.tiendasonido.catalogservice.dto.ProductoRequest;
import cl.tiendasonido.catalogservice.dto.ProductoResponse;
import cl.tiendasonido.catalogservice.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Consulta pública; crear, editar y eliminar requiere el rol ADMIN")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Lista los productos, con filtro opcional por categoría")
    public List<ProductoResponse> listar(
            @RequestParam(name = "categoriaId", required = false) Long categoriaId) {
        return productoService.listar(categoriaId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un producto por su id")
    @ApiResponse(responseCode = "404", description = "El producto no existe")
    public ProductoResponse obtener(@PathVariable("id") Long id) {
        return productoService.obtener(id);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un producto")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "404", description = "La categoría indicada no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe un producto con ese código")
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        return productoService.crear(request);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualiza un producto (nombre, precio, stock, categoría, etc.)")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "404", description = "El producto o la categoría no existen")
    @ApiResponse(responseCode = "409", description = "Ya existe otro producto con ese código")
    public ProductoResponse actualizar(@PathVariable("id") Long id, @Valid @RequestBody ProductoRequest request) {
        return productoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un producto")
    @ApiResponse(responseCode = "404", description = "El producto no existe")
    public void eliminar(@PathVariable("id") Long id) {
        productoService.eliminar(id);
    }
}
