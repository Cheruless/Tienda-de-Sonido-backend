package cl.tiendasonido.catalogservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.tiendasonido.catalogservice.dto.ProductoRequest;
import cl.tiendasonido.catalogservice.dto.ProductoResponse;
import cl.tiendasonido.catalogservice.entity.Categoria;
import cl.tiendasonido.catalogservice.entity.Producto;
import cl.tiendasonido.catalogservice.exception.ConflictoException;
import cl.tiendasonido.catalogservice.exception.RecursoNoEncontradoException;
import cl.tiendasonido.catalogservice.repository.CategoriaRepository;
import cl.tiendasonido.catalogservice.repository.ProductoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProductoService productoService;

    private ProductoRequest solicitud() {
        return new ProductoRequest("GA001", "Guitarra Acústica Folk", "Yamaha", "F310",
                "Guitarra de estudio", 129990, 8, 1L);
    }

    @Test
    void crearRechazaUnCodigoDuplicado() {
        when(productoRepository.existsByCodigoIgnoreCase("GA001")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> productoService.crear(solicitud()));

        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void crearFallaSiLaCategoriaNoExiste() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> productoService.crear(solicitud()));

        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void crearGuardaElProductoConSusDatos() {
        Categoria categoria = new Categoria(1L, "Guitarras");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocacion -> {
            Producto producto = invocacion.getArgument(0);
            producto.setId(10L);
            return producto;
        });

        ProductoResponse respuesta = productoService.crear(solicitud());

        assertEquals(10L, respuesta.id());
        assertEquals("GA001", respuesta.codigo());
        assertEquals(129990, respuesta.precio().intValue());
        assertEquals(8, respuesta.stock().intValue());
        assertEquals("Guitarras", respuesta.categoria());
    }

    @Test
    void eliminarFallaSiElProductoNoExiste() {
        when(productoRepository.existsById(5L)).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class, () -> productoService.eliminar(5L));

        verify(productoRepository, never()).deleteById(any(Long.class));
    }
}
