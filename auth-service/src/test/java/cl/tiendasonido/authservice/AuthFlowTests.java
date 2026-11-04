package cl.tiendasonido.authservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    // ---------- Helpers ----------

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String registerBody(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
    }

    private String payloadOf(String token) {
        byte[] decoded = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- Login ----------

    @Test
    void loginAdminDevuelveTokenConSoloElRol() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));

        String payload = payloadOf(login("admin", "admin123"));
        assertTrue(payload.contains("ROLE_ADMIN"));
        assertFalse(payload.contains("FACTOR_PASSWORD"));
    }

    @Test
    void loginClienteTraeRolCliente() throws Exception {
        String payload = payloadOf(login("cliente", "cliente123"));
        assertTrue(payload.contains("ROLE_CLIENTE"));
        assertFalse(payload.contains("ROLE_ADMIN"));
    }

    @Test
    void loginConPasswordIncorrectaDa401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin", "mala")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }

    @Test
    void loginConCamposVaciosDa400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"))
                .andExpect(jsonPath("$.detalles.username").exists())
                .andExpect(jsonPath("$.detalles.password").exists());
    }

    @Test
    void loginConJsonRotoDa400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": "))
                .andExpect(status().isBadRequest());
    }

    // ---------- Registro ----------

    @Test
    void registroCreaUsuarioYPuedeIniciarSesion() throws Exception {
        String username = unique("user");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, username + "@correo.cl", "secreto123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        String payload = payloadOf(login(username, "secreto123"));
        assertTrue(payload.contains("ROLE_CLIENTE"));
    }

    @Test
    void registroConUsernameDuplicadoDa409() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("admin", unique("x") + "@correo.cl", "secreto123")))
                .andExpect(status().isConflict());
    }

    @Test
    void registroConEmailDuplicadoDa409() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(unique("user"), "cliente@tiendasonido.cl", "secreto123")))
                .andExpect(status().isConflict());
    }

    @Test
    void registroConDatosInvalidosDa400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("ab", "no-es-email", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.username").exists())
                .andExpect(jsonPath("$.detalles.email").exists())
                .andExpect(jsonPath("$.detalles.password").exists());
    }

    // ---------- Acceso por token y por rol ----------

    @Test
    void usersMeSinTokenDa401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMeConTokenAlteradoDa401() throws Exception {
        String token = login("cliente", "cliente123");

        // Se cambia el primer carácter de la firma, lo que sí altera sus bytes
        String[] partes = token.split("\\.");
        char primero = partes[2].charAt(0);
        char nuevo = (primero == 'A') ? 'B' : 'A';
        String tokenAlterado = partes[0] + "." + partes[1] + "." + nuevo + partes[2].substring(1);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + tokenAlterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMeConTokenDevuelveLosDatosDelUsuario() throws Exception {
        String token = login("cliente", "cliente123");
        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cliente"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"));
    }

    @Test
    void listarUsuariosSinTokenDa401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarUsuariosComoClienteDa403() throws Exception {
        String token = login("cliente", "cliente123");
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarUsuariosComoAdminDa200() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- Administración ----------

    @Test
    void adminDesactivaUsuarioYEsteYaNoPuedeIniciarSesion() throws Exception {
        // Se usa un usuario recién creado para no alterar a admin ni a cliente
        String username = unique("baja");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, username + "@correo.cl", "secreto123")))
                .andExpect(status().isCreated());

        String userToken = login(username, "secreto123");
        String meBody = mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(meBody, "$.id");

        String adminToken = login("admin", "admin123");
        mockMvc.perform(patch("/users/" + id + "/activo")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "secreto123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInexistenteDa404() throws Exception {
        String adminToken = login("admin", "admin123");
        mockMvc.perform(patch("/users/999999/activo")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\": false}"))
                .andExpect(status().isNotFound());
    }
}