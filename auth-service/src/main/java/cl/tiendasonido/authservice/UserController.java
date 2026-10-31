package cl.tiendasonido.authservice;

import cl.tiendasonido.authservice.entity.Rol;
import cl.tiendasonido.authservice.entity.Usuario;
import cl.tiendasonido.authservice.repository.RolRepository;
import cl.tiendasonido.authservice.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
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

    public UserController(UsuarioRepository usuarioRepository, RolRepository rolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
    }

    // ---------- DTOs ----------

    public record UserResponse(Long id, String username, String email,
                               boolean activo, List<String> roles) {}

    public record ActivoRequest(@NotNull(message = "El campo activo es obligatorio") Boolean activo) {}

    public record RolesRequest(@NotEmpty(message = "Debes indicar al menos un rol") Set<String> roles) {}

    private UserResponse toResponse(Usuario u) {
        return new UserResponse(
            u.getId(),
            u.getUsername(),
            u.getEmail(),
            u.isActivo(),
            u.getRoles().stream().map(Rol::getNombre).sorted().toList());
    }

    // ---------- Cualquier usuario autenticado ----------

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return usuarioRepository.findByUsername(authentication.getName())
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Usuario no encontrado"));
        }
        if (!request.activo() && usuario.getUsername().equals(authentication.getName())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "No puedes desactivar tu propia cuenta"));
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Usuario no encontrado"));
        }

        Set<Rol> nuevosRoles = new HashSet<>();
        for (String nombre : request.roles()) {
            var rol = rolRepository.findByNombre(nombre.trim().toUpperCase()).orElse(null);
            if (rol == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "El rol '" + nombre + "' no existe"));
            }
            nuevosRoles.add(rol);
        }

        boolean esUnoMismo = usuario.getUsername().equals(authentication.getName());
        boolean seQuedaSinAdmin = nuevosRoles.stream().noneMatch(r -> r.getNombre().equals("ADMIN"));
        if (esUnoMismo && seQuedaSinAdmin) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "No puedes quitarte a ti mismo el rol ADMIN"));
        }

        usuario.setRoles(nuevosRoles);
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(toResponse(usuario));
    }
}