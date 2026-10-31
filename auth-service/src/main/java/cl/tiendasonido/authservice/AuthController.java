package cl.tiendasonido.authservice;

import cl.tiendasonido.authservice.entity.Rol;
import cl.tiendasonido.authservice.entity.Usuario;
import cl.tiendasonido.authservice.repository.RolRepository;
import cl.tiendasonido.authservice.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UsuarioRepository usuarioRepository,
                          RolRepository rolRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- DTOs ----------

    public record LoginRequest(
        @NotBlank(message = "El username es obligatorio") String username,
        @NotBlank(message = "La contraseña es obligatoria") String password) {}

    public record LoginResponse(String token, String type, long expiresInSeconds) {}

    public record RegisterRequest(
        @NotBlank(message = "El username es obligatorio")
        @Size(min = 3, max = 50) String username,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Email inválido") String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, max = 100) String password) {}

    // ---------- Endpoints ----------

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            String token = jwtUtil.generateToken(
                authentication.getName(), authentication.getAuthorities());

            return ResponseEntity.ok(new LoginResponse(token, "Bearer", 3600));

        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales inválidas"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El username ya está en uso"));
        }
        if (usuarioRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El email ya está registrado"));
        }

        Rol rolCliente = rolRepository.findByNombre("CLIENTE")
            .orElseGet(() -> rolRepository.save(new Rol("CLIENTE")));

        Usuario usuario = new Usuario();
        usuario.setUsername(request.username());
        usuario.setEmail(request.email());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRoles(Set.of(rolCliente));

        try {
            usuarioRepository.save(usuario);
        } catch (DataIntegrityViolationException ex) {
            // Cubre el caso de dos registros simultáneos con el mismo username o email
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El username o el email ya existen"));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("message", "Usuario '" + request.username() + "' registrado exitosamente"));
    }
}