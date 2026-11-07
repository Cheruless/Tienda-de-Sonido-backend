package cl.tiendasonido.cartservice.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregado de dominio: un documento de MongoDB por carrito, con los items
 * embebidos dentro del mismo documento.
 *
 * <p><strong>¿Por qué un solo documento?:</strong> las operaciones del carrito
 * (leer, agregar, quitar, totalizar) siempre trabajan sobre el carrito de un
 * solo usuario. Como no hay consultas que crucen carritos de usuarios
 * distintos ni relaciones con otras colecciones, embeber {@code items} como
 * array dentro del documento evita joins y hace que cada operación sea
 * atómica sobre un único documento.</p>
 *
 * <p><strong>Concurrencia:</strong> el campo {@link #version} habilita el
 * <em>optimistic locking</em> de Spring Data MongoDB. Si dos peticiones
 * leen la misma versión y guardan, solo una gana; la otra recibe
 * {@code OptimisticLockingFailureException} y {@code CartService} reintenta.
 * Ver {@link cl.tiendasonido.cartservice.service.CartService}.</p>
 *
 * @author Exequiel Pulgar Cerda
 * @see cl.tiendasonido.cartservice.service.CartService
 */
@Document(collection = "carts")
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    /** Identificador del documento en MongoDB ({@code _id}). */
    @Id
    private String id;

    /**
     * Dueño del carrito: el claim {@code sub} del JWT, que según el contrato
     * acordado con auth-service es el <strong>correo electrónico</strong>
     * del usuario. Un usuario tiene a lo más un carrito.
     *
     * <p>El índice único es obligatorio: sin él, dos peticiones simultáneas
     * de un usuario sin carrito podrían <em>insertar dos documentos</em>.
     * El índice convertiría esa segunda inserción en un
     * {@code DuplicateKeyException}, que {@code CartService} maneja.</p>
     */
    @Indexed(unique = true)
    private String userId;

    /** Items embebidos del carrito. Nunca se persisten totales: se calculan al responder. */
    private List<Item> items = new ArrayList<>();

    /**
     * Optimistic locking: inicia en {@code 0} al insertar y se incrementa
     * automáticamente en cada {@code save()}. No debe exponerse en los DTOs
     * de la API: es un detalle interno de persistencia.
     */
    @Version
    private Long version;

    /** Momento de creación del carrito (UTC). */
    private Instant createdAt;

    /** Momento de la última modificación; lo actualiza {@code CartService} antes de guardar. */
    private Instant updatedAt;

    /**
     * Crea un carrito vacío para el usuario indicado.
     *
     * @param userId correo electrónico del dueño (claim {@code sub} del JWT)
     */
    public Cart(String userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Agrega el item al carrito; si el producto ya existe, suma la cantidad
     * en lugar de duplicar la línea.
     *
     * <p>Los datos de catálogo ({@code productName}, {@code unitPrice}) se
     * guardan como <em>snapshot</em>: si el precio cambia después en
     * catálogo, el carrito conserva el precio con el que se agregó. Es
     * intencional, porque refleja lo que el usuario vio al agregarlo.</p>
     *
     * @param nuevo item a agregar o fusionar
     */
    public void addOrMerge(Item nuevo) {
        items.stream()
                .filter(item -> item.getProductId().equals(nuevo.getProductId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + nuevo.getQuantity()),
                        () -> items.add(nuevo));
    }

    /**
     * Elimina del carrito la línea del producto indicado.
     *
     * @param productId identificador del producto a quitar
     * @return {@code true} si había una línea para ese producto; {@code false} si no estaba
     */
    public boolean remove(String productId) {
        return items.removeIf(item -> item.getProductId().equals(productId));
    }

    /**
     * Suma los totales de cada línea ({@code unitPrice * quantity}).
     *
     * <p>Se calcula en cada respuesta en lugar de guardarlo: así nunca hay
     * un total desactualizado en la BD.</p>
     *
     * @return total del carrito en CLP (entero, sin decimales)
     */
    public long total() {
        return items.stream().mapToLong(Item::lineTotal).sum();
    }

    /**
     * Línea del carrito: un producto con su cantidad.
     *
     * <p>Es una clase estática anidada porque embebe directamente en el
     * documento de MongoDB como elemento del array {@code items}.</p>
     *
     * @author Exequiel Pulgar Cerda
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        /** Identificador del producto en catálogo ({@code String} para no acoplarse al tipo de id de catálogo). */
        private String productId;

        /** Snapshot del nombre del producto al momento de agregarlo. */
        private String productName;

        /** Snapshot del precio unitario en CLP (entero; nunca {@code double} para dinero). */
        private Long unitPrice;

        /** Cantidad pedida; siempre {@code >= 1} (lo garantiza la validación del DTO). */
        private int quantity;

        /**
         * @param productId  identificador del producto
         * @param productName nombre copiado de catálogo
         * @param unitPrice   precio unitario en CLP copiado de catálogo
         * @param quantity    cantidad solicitada ({@code >= 1})
         */
        public Item(String productId, String productName, Long unitPrice, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        /**
         * Total de la línea: precio unitario por cantidad.
         *
         * @return importe en CLP (entero)
         */
        public long lineTotal() {
            return unitPrice * quantity;
        }
    }
}
