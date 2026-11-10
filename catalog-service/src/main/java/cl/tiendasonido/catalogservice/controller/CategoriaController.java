package cl.tiendasonido.catalogservice.controller;

import cl.tiendasonido.catalogservice.dto.CategoriaRequest;
import cl.tiendasonido.catalogservice.dto.CategoriaResponse;
import cl.tiendasonido.catalogservice.service.CategoriaService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@Tag(name = "Categorías", description = "Consulta pública; crear, editar y eliminar requiere el rol ADMIN")
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    @Operation(summary = "Lista todas las categorías")
    public List<CategoriaResponse> listar() {
        return categoriaService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una categoría por su id")
    @ApiResponse(responseCode = "404", description = "La categoría no existe")
    public CategoriaResponse obtener(@PathVariable("id") Long id) {
        return categoriaService.obtener(id);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una categoría")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre")
    public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crear(request);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualiza una categoría")
    @ApiResponse(responseCode = "404", description = "La categoría no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese nombre")
    public CategoriaResponse actualizar(@PathVariable("id") Long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una categoría sin productos asociados")
    @ApiResponse(responseCode = "404", description = "La categoría no existe")
    @ApiResponse(responseCode = "409", description = "La categoría tiene productos asociados")
    public void eliminar(@PathVariable("id") Long id) {
        categoriaService.eliminar(id);
    }
}
