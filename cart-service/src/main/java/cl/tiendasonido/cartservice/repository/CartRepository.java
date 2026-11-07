package cl.tiendasonido.cartservice.repository;

import cl.tiendasonido.cartservice.domain.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Acceso de datos a la colección {@code carts} de MongoDB.
 *
 * <p>Spring Data implementa esta interfaz automáticamente en tiempo de
 * ejecución: no hay que escribir SQL/consultas ni una clase concreta. El
 * nombre del método se convierte en la consulta
 * ({@code findByUserId} &rarr; "buscar donde userId = ?"), por lo que
 * renombrarlo rompe en compilación, no en producción.</p>
 *
 * <p>Extiende de {@code MongoRepository}, lo que trae los CRUD básicos
 * ({@code save}, {@code findById}, {@code deleteById}...). Lo propio de este
 * dominio es {@link #findByUserId(String)}, la única consulta que el
 * carrito necesita.</p>
 *
 * @author Exequiel Pulgar Cerda
 */
public interface CartRepository extends MongoRepository<Cart, String> {

    /**
     * Busca el carrito de un usuario por su correo (claim {@code sub} del JWT).
     *
     * <p>Como {@code userId} tiene índice único, a lo más puede devolver un
     * resultado, de ahí que retorne {@code Optional} y no una lista.</p>
     *
     * @param userId correo del dueño del carrito
     * @return el carrito si existe, vacío en caso contrario
     */
    Optional<Cart> findByUserId(String userId);
}
