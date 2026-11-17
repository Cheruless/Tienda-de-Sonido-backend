package cl.tiendasonido.catalogservice.repository;

import cl.tiendasonido.catalogservice.entity.Producto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByCategoriaId(Long categoriaId);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    boolean existsByCategoriaId(Long categoriaId);

    /**
     * Descuenta stock de forma ATÓMICA: una sola sentencia SQL que solo
     * aplica si alcanza el stock ({@code stock >= cantidad}).
     *
     * <p>Esto evita el patrón peligroso "leer el stock, calcular en Java y
     * guardar": con dos compras simultáneas ambas leerían el mismo stock y
     * ambas podrían vender la última unidad. Aquí la base de datos resuelve
     * el conflicto: una de las dos update afecta 0 filas.</p>
     *
     * @param id       id del producto
     * @param cantidad unidades a descontar (debe ser &ge; 1; lo garantiza la validación del DTO)
     * @return 1 si se descontó, 0 si no alcanzaba el stock o el producto no existe
     */
    @Modifying
    @Query("update Producto p set p.stock = p.stock - :cantidad where p.id = :id and p.stock >= :cantidad")
    int descontarStock(@Param("id") Long id, @Param("cantidad") int cantidad);
}
