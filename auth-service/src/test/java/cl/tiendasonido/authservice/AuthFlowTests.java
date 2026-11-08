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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTests {

    private static final String ADMIN_EMAIL = "admin@sonidovivo.cl";
    private static final String ADMIN_PASS = "Admin123Password";
    private static final String DEMO_EMAIL = "demo@sonidovivo.cl";
    private static final String DEMO_PASS = "Demo123Password";

    @Autowired
    private MockMvc mockMvc;

    // ---------- Helpers ----------

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String registerBody(String nombre, String email, String password) {
        return "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
    }

    private String payloadOf(String token) {
        byte[] decoded = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String uniqueEmail() {
        return "user" + UUID.randomUUID().toString().substring(0, 8) + "@correo.cl";
    }

    private Integer idDe(String token) throws Exception {
        String body = mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // ---------- Login ----------

    @Test
    void loginAdminDevuelveTokenYDatosDelUsuario() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ADMIN_EMAIL, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.usuario.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.usuario.nombre").value("Administrador"))
                .andExpect(jsonPath("$.usuario.rol").value("admin"));

        String payload = payloadOf(login(ADMIN_EMAIL, ADMIN_PASS));
        assertTrue(payload.contains(ADMIN_EMAIL));
        assertTrue(payload.contains("ROLE_ADMIN"));
        assertFalse(payload.contains("FACTOR_PASSWORD"));
    }

    @Test
    void loginClienteTraeRolCliente() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(DEMO_EMAIL, DEMO_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("cliente"));

        String payload = payloadOf(login(DEMO_EMAIL, DEMO_PASS));
        assertTrue(payload.contains("ROLE_CLIENTE"));
        assertFalse(payload.contains("ROLE_ADMIN"));
    }

    @Test
    void loginNoDistingueMayusculasEnElCorreo() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("ADMIN@SonidoVivo.CL", ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(ADMIN_EMAIL));
    }

    @Test
    void loginConPasswordIncorrectaDa401() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ADMIN_EMAIL, "mala")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void loginConCamposVaciosDa400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"))
                .andExpect(jsonPath("$.detalles.email").exists())
                .andExpect(jsonPath("$.detalles.password").exists());
    }

    @Test
    void loginConJsonRotoDa400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": "))
                .andExpect(status().isBadRequest());
    }

    // ---------- Registro ----------

    @Test
    void registroCreaUsuarioYPuedeIniciarSesion() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Juan Perez", email, "secreto123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "secreto123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombre").value("Juan Perez"))
                .andExpect(jsonPath("$.usuario.rol").value("cliente"));
    }

    @Test
    void registroConEmailDuplicadoDa409() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Otro Admin", ADMIN_EMAIL, "secreto123")))
                .andExpect(status().isConflict());
    }

    @Test
    void registroConEmailDuplicadoEnOtraCapitalizacionDa409() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Otro Demo", "DEMO@SonidoVivo.cl", "secreto123")))
                .andExpect(status().isConflict());
    }

    @Test
    void registroConDatosInvalidosDa400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("ab", "no-es-email", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.nombre").exists())
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
        String token = login(DEMO_EMAIL, DEMO_PASS);

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
        String token = login(DEMO_EMAIL, DEMO_PASS);
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEMO_EMAIL))
                .andExpect(jsonPath("$.nombre").value("Usuario Demo"))
                .andExpect(jsonPath("$.rol").value("cliente"))
                .andExpect(jsonPath("$.registradoEn").isString());
    }

    @Test
    void listarUsuariosSinTokenDa401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarUsuariosComoClienteDa403() throws Exception {
        String token = login(DEMO_EMAIL, DEMO_PASS);
        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarUsuariosComoAdminDa200() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASS);
        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].nombre").exists())
                .andExpect(jsonPath("$[0].rol").exists());
    }

    // ---------- Administración ----------

    @Test
    void adminDesactivaUsuarioYEsteYaNoPuedeIniciarSesion() throws Exception {
        // Se usa un usuario recién creado para no alterar las cuentas semilla
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Usuario Baja", email, "secreto123")))
                .andExpect(status().isCreated());

        Integer id = idDe(login(email, "secreto123"));

        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        mockMvc.perform(patch("/users/" + id + "/activo")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "secreto123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInexistenteDa404() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        mockMvc.perform(patch("/users/999999/activo")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminNoPuedeDesactivarseASiMismo() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        Integer id = idDe(adminToken);

        mockMvc.perform(patch("/users/" + id + "/activo")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\": false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarRolesConRolInexistenteDa400() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        Integer idDemo = idDe(login(DEMO_EMAIL, DEMO_PASS));

        mockMvc.perform(put("/users/" + idDemo + "/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": [\"SUPERMAN\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Edición y eliminación de usuarios ----------

    private String updateBody(String nombre, String email, String password, String rol) {
        String pass = password == null ? "" : ",\"password\":\"" + password + "\"";
        return "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\"" + pass
                + ",\"rol\":\"" + rol + "\"}";
    }

    private Integer registrarYObtenerId(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Usuario Prueba", email, "secreto123")))
                .andExpect(status().isCreated());
        return idDe(login(email, "secreto123"));
    }

    @Test
    void adminEditaUsuarioYPuedeIniciarSesionConLosNuevosDatos() throws Exception {
        String email = uniqueEmail();
        Integer id = registrarYObtenerId(email);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        String nuevoEmail = uniqueEmail();

        mockMvc.perform(put("/users/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Nombre Nuevo", nuevoEmail, "nueva456", "cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Nuevo"))
                .andExpect(jsonPath("$.email").value(nuevoEmail))
                .andExpect(jsonPath("$.rol").value("cliente"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(nuevoEmail, "nueva456")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "secreto123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editarSinPasswordMantieneLaAnterior() throws Exception {
        String email = uniqueEmail();
        Integer id = registrarYObtenerId(email);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);

        mockMvc.perform(put("/users/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Solo Cambia Nombre", email, null, "cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Solo Cambia Nombre"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "secreto123")))
                .andExpect(status().isOk());
    }

    @Test
    void editarConCorreoDeOtroUsuarioDa409() throws Exception {
        Integer id = registrarYObtenerId(uniqueEmail());
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);

        mockMvc.perform(put("/users/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Usuario Prueba", DEMO_EMAIL, null, "cliente")))
                .andExpect(status().isConflict());
    }

    @Test
    void adminNoPuedeQuitarseElRolAdmin() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        Integer idAdmin = idDe(adminToken);

        mockMvc.perform(put("/users/" + idAdmin)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Administrador", ADMIN_EMAIL, null, "cliente")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminNoPuedeCambiarSuPropioCorreo() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        Integer idAdmin = idDe(adminToken);

        mockMvc.perform(put("/users/" + idAdmin)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Administrador", "otro@sonidovivo.cl", null, "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteNoPuedeEditarNiEliminar() throws Exception {
        String clienteToken = login(DEMO_EMAIL, DEMO_PASS);

        mockMvc.perform(put("/users/1")
                .header("Authorization", "Bearer " + clienteToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Hacker Perez", "hacker@correo.cl", null, "admin")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/users/1")
                .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEliminaUsuarioYEsteYaNoExiste() throws Exception {
        String email = uniqueEmail();
        Integer id = registrarYObtenerId(email);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);

        mockMvc.perform(delete("/users/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "secreto123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/users/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminNoPuedeEliminarseASiMismo() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);
        Integer idAdmin = idDe(adminToken);

        mockMvc.perform(delete("/users/" + idAdmin)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarConRolInvalidoDa400() throws Exception {
        Integer id = registrarYObtenerId(uniqueEmail());
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASS);

        mockMvc.perform(put("/users/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Usuario Prueba", uniqueEmail(), null, "superman")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elTokenSeFirmaConHS256() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASS);
        String header = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertTrue(header.contains("\"HS256\""));
    }
}