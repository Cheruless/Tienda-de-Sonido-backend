package cl.tiendasonido.catalogservice.service;

import cl.tiendasonido.catalogservice.dto.ProductoRequest;
import cl.tiendasonido.catalogservice.dto.ProductoResponse;
import cl.tiendasonido.catalogservice.entity.Categoria;
import cl.tiendasonido.catalogservice.entity.Producto;
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
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(Long categoriaId) {
        List<Producto> productos = categoriaId == null
                ? productoRepository.findAll()
                : productoRepository.findByCategoriaId(categoriaId);
        return productos.stream().map(ProductoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.from(buscar(id));
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        String codigo = request.codigo().trim();
        if (productoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new ConflictoException("Ya existe un producto con el código " + codigo);
        }
        Categoria categoria = buscarCategoria(request.categoriaId());
        Producto producto = new Producto();
        aplicarDatos(producto, codigo, request, categoria);
        return ProductoResponse.from(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscar(id);
        String codigo = request.codigo().trim();
        if (productoRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new ConflictoException("Ya existe un producto con el código " + codigo);
        }
        Categoria categoria = buscarCategoria(request.categoriaId());
        aplicarDatos(producto, codigo, request, categoria);
        return ProductoResponse.from(productoRepository.save(producto));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!productoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Producto no encontrado con id " + id);
        }
        productoRepository.deleteById(id);
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con id " + id));
    }

    private Categoria buscarCategoria(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada con id " + id));
    }

    private void aplicarDatos(Producto producto, String codigo, ProductoRequest request, Categoria categoria) {
        producto.setCodigo(codigo);
        producto.setNombre(request.nombre().trim());
        producto.setMarca(request.marca().trim());
        producto.setModelo(limpiar(request.modelo()));
        producto.setDescripcion(limpiar(request.descripcion()));
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setCategoria(categoria);
    }

    private String limpiar(String texto) {
        return texto == null ? null : texto.trim();
    }
}
