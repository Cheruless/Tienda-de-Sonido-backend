package cl.tiendasonido.cartservice.web;

import cl.tiendasonido.cartservice.config.CorsConfig;
import cl.tiendasonido.cartservice.config.SecurityConfig;
import cl.tiendasonido.cartservice.service.CartService;
import cl.tiendasonido.cartservice.web.dto.CartItemResponse;
import cl.tiendasonido.cartservice.web.dto.CartResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, CorsConfig.class})
class CartControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject("user@example.com"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCarritoConJwt() throws Exception {
        when(cartService.getCart("user@example.com"))
                .thenReturn(new CartResponse(List.of(), 0L));

        mockMvc.perform(get("/api/cart").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void agregarItemConJwt() throws Exception {
        when(cartService.addItem(any(), any()))
                .thenReturn(new CartResponse(
                        List.of(new CartItemResponse(
                                "demo-1", "Guitarra electrica", 199_990L, 2, 399_980L)),
                        399_980L));

        mockMvc.perform(post("/api/cart/items")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"demo-1\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].productId").value("demo-1"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(199_990))
                .andExpect(jsonPath("$.total").value(399_980));
    }

    @Test
    void validacionDevuelve400() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }
}
