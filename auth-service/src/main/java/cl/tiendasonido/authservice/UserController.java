package cl.tiendasonido.authservice;

import cl.tiendasonido.authservice.entity.Rol;
import cl.tiendasonido.authservice.entity.Usuario;
import cl.tiendasonido.authservice.repository.RolRepository;
import cl.tiendasonido.authservice.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UsuarioRepository usuarioRepository,
                          RolRepository rolRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- DTOs ----------

    public record UserResponse(Long id, String nombre, String email, boolean activo,
                               String rol, List<String> roles, String registradoEn) {}

    public record ActivoRequest(@NotNull(message = "El campo activo es obligatorio") Boolean activo) {}

    public record RolesRequest(@NotEmpty(message = "Debes indicar al menos un rol") Set<String> roles) {}

    public record UpdateUserRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres") String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo inválido")
        @Size(max = 120, message = "El correo es demasiado largo") String email,

        // Opcional: si no viene o viene vacía, se mantiene la contraseña actual
        @Pattern(regexp = "^$|.{6,100}", message = "La contraseña debe tener entre 6 y 100 caracteres") String password,

        @NotBlank(message = "El rol es obligatorio") String rol) {}

    private UserResponse toResponse(Usuario u) {
        return new UserResponse(
            u.getId(),
            u.getNombre(),
            u.getEmail(),
            u.isActivo(),
            tieneRol(u, "ADMIN") ? "admin" : "cliente",
            u.getRoles().stream().map(Rol::getNombre).sorted().toList(),
            u.getFechaCreacion().toString());
    }

    private boolean tieneRol(Usuario u, String nombre) {
        return u.getRoles().stream().anyMatch(r -> r.getNombre().equals(nombre));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", mensaje));
    }

    // ---------- Cualquier usuario autenticado ----------

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return usuarioRepository.findByEmail(authentication.getName())
            .map(u -> ResponseEntity.ok(toResponse(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ---------- Solo ADMIN (se protege en SecurityConfig) ----------

    @GetMapping
    public List<UserResponse> listar() {
        return usuarioRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PatchMapping("/{id}/activo")
    @Transactional
    public ResponseEntity<?> cambiarActivo(@PathVariable Long id,
                                           @Valid @RequestBody ActivoRequest request,
                                           Authentication authentication) {
        var usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) {
            return error(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        if (!request.activo() && usuario.getEmail().equals(authentication.getName())) {
            return error(HttpStatus.BAD_REQUEST, "No puedes desactivar tu propia cuenta");
        }

        usuario.setActivo(request.activo());
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(toResponse(usuario));
    }

    @PutMapping("/{id}/roles")
    @Transactional
    public ResponseEntity<?> cambiarRoles(@PathVariable Long id,
                                          @Valid @RequestBody RolesRequest request,
                                          Authentication authentication) {
        var usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) {
            return error(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        Set<Rol> nuevosRoles = new HashSet<>();
        for (String nombre : request.roles()) {
            var rol = rolRepository.findByNombre(nombre.trim().toUpperCase()).orElse(null);
            if (rol == null) {
                return error(HttpStatus.BAD_REQUEST, "El rol '" + nombre + "' no existe");
            }
            nuevosRoles.add(rol);
        }

        boolean esUnoMismo = usuario.getEmail().equals(authentication.getName());
        boolean seQuedaSinAdmin = nuevosRoles.stream().noneMatch(r -> r.getNombre().equals("ADMIN"));
        if (esUnoMismo && seQuedaSinAdmin) {
            return error(HttpStatus.BAD_REQUEST, "No puedes quitarte a ti mismo el rol ADMIN");
        }

        usuario.setRoles(nuevosRoles);
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(toResponse(usuario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRequest request,
                                        Authentication authentication) {
        var usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) {
            return error(HttpStatus.NOT_FOUND, "El usuario no existe.");
        }

        String rolSolicitado = request.rol().trim().toLowerCase();
        if (!rolSolicitado.equals("admin") && !rolSolicitado.equals("cliente")) {
            return error(HttpStatus.BAD_REQUEST, "El rol debe ser 'admin' o 'cliente'.");
        }

        String nuevoEmail = request.email().trim().toLowerCase();
        boolean esUnoMismo = usuario.getEmail().equals(authentication.getName());
        boolean emailCambia = !nuevoEmail.equals(usuario.getEmail());
        boolean eraAdmin = tieneRol(usuario, "ADMIN");
        boolean seraAdmin = rolSolicitado.equals("admin");

        // El token del admin conectado guarda su correo actual: si cambiara, quedaría desfasado.
        if (esUnoMismo && emailCambia) {
            return error(HttpStatus.BAD_REQUEST, "No puedes cambiar tu propio correo desde el panel.");
        }
        if (emailCambia && usuarioRepository.existsByEmail(nuevoEmail)) {
            return error(HttpStatus.CONFLICT, "Ese correo ya está registrado por otro usuario.");
        }
        if (esUnoMismo && !seraAdmin) {
            return error(HttpStatus.BAD_REQUEST, "No puedes quitarte a ti mismo el rol de administrador.");
        }
        if (eraAdmin && !seraAdmin && usuarioRepository.countByRolesNombre("ADMIN") <= 1) {
            return error(HttpStatus.BAD_REQUEST, "Debe quedar al menos un administrador activo en la tienda.");
        }

        Rol rol = rolRepository.findByNombre(seraAdmin ? "ADMIN" : "CLIENTE")
            .orElseGet(() -> rolRepository.save(new Rol(seraAdmin ? "ADMIN" : "CLIENTE")));

        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(nuevoEmail);
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }
        usuario.setRoles(new HashSet<>(Set.of(rol)));

        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            return error(HttpStatus.CONFLICT, "Ese correo ya está registrado por otro usuario.");
        }

        return ResponseEntity.ok(toResponse(usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, Authentication authentication) {
        var usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null) {
            return error(HttpStatus.NOT_FOUND, "El usuario no existe.");
        }
        if (usuario.getEmail().equals(authentication.getName())) {
            return error(HttpStatus.BAD_REQUEST, "No puedes eliminar tu propia cuenta desde el panel.");
        }
        if (tieneRol(usuario, "ADMIN") && usuarioRepository.countByRolesNombre("ADMIN") <= 1) {
            return error(HttpStatus.BAD_REQUEST, "No puedes eliminar al último administrador de la tienda.");
        }

        usuarioRepository.delete(usuario);
        return ResponseEntity.ok(Map.of("message", "El usuario \"" + usuario.getNombre() + "\" fue eliminado."));
    }
}