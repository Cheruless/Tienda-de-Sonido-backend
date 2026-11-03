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

@Document(collection = "carts")
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    @Id
    private String id;

    /** sub del JWT (email del usuario). Un carrito por usuario. */
    @Indexed(unique = true)
    private String userId;

    private List<Item> items = new ArrayList<>();

    /** Optimistic locking: 0 al insertar, se incrementa automaticamente en cada save(). */
    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;

    public Cart(String userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Agrega el item o suma la cantidad si el producto ya esta en el carrito.
     * Se conserva el snapshot (nombre/precio) de la primera vez que se agrego.
     */
    public void addOrMerge(Item nuevo) {
        items.stream()
                .filter(item -> item.getProductId().equals(nuevo.getProductId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + nuevo.getQuantity()),
                        () -> items.add(nuevo));
    }

    public boolean remove(String productId) {
        return items.removeIf(item -> item.getProductId().equals(productId));
    }

    public long total() {
        return items.stream().mapToLong(Item::lineTotal).sum();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private String productId;
        private String productName; // snapshot de catalogo
        private Long unitPrice;     // CLP entero, snapshot de catalogo
        private int quantity;

        public Item(String productId, String productName, Long unitPrice, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        public long lineTotal() {
            return unitPrice * quantity;
        }
    }
}
