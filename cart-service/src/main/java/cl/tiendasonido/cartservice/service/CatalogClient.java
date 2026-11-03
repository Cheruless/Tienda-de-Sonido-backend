package cl.tiendasonido.cartservice.service;

/**
 * Puerto hacia catalog-service. La implementacion HTTP llegara cuando exista
 * catalog-service; mientras tanto se usa FakeCatalogClient.
 */
public interface CatalogClient {

    ProductSummary getProduct(String productId);
}
