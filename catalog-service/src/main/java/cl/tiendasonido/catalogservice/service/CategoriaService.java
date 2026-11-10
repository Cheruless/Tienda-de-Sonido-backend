package cl.tiendasonido.catalogservice.service;

import cl.tiendasonido.catalogservice.dto.CategoriaRequest;
import cl.tiendasonido.catalogservice.dto.CategoriaResponse;
import cl.tiendasonido.catalogservice.entity.Categoria;
import cl.tiendasonido.catalogservice.exception.ConflictoException;
import cl.tiendasonido.catalogservice.exception.RecursoNoEncontradoException;
import cl.tiendasonido.catalogservice.repository.CategoriaRepository;
import cl.tiendasonido.catalogservice.repository.ProductoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAll().stream().map(CategoriaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtener(Long id) {
        return CategoriaResponse.from(buscar(id));
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("Ya existe una categoría con el nombre " + nombre);
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = buscar(id);
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ConflictoException("Ya existe una categoría con el nombre " + nombre);
        }
        categoria.setNombre(nombre);
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Categoría no encontrada con id " + id);
        }
        if (productoRepository.existsByCategoriaId(id)) {
            throw new ConflictoException("No se puede eliminar una categoría que tiene productos asociados");
        }
        categoriaRepository.deleteById(id);
    }

    private Categoria buscar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada con id " + id));
    }
}
