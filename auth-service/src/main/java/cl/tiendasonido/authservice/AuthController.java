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
        @NotBlank(message = "El correo es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {}

    public record UsuarioResumen(Long id, String nombre, String email, String rol) {}

    public record LoginResponse(String token, String type, long expiresInSeconds, UsuarioResumen usuario) {}

    public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres") String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo inválido")
        @Size(max = 120, message = "El correo es demasiado largo") String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, max = 100, message = "La contraseña debe tener entre 6 y 100 caracteres") String password) {}

    // ---------- Endpoints ----------

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

            String token = jwtUtil.generateToken(
                authentication.getName(), authentication.getAuthorities());

            Usuario usuario = usuarioRepository.findByEmail(authentication.getName()).orElseThrow();
            var resumen = new UsuarioResumen(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), rolUnico(usuario));

            return ResponseEntity.ok(new LoginResponse(token, "Bearer", 3600, resumen));

        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Correo o contraseña incorrectos."));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El correo electrónico ya está registrado."));
        }

        Rol rolCliente = rolRepository.findByNombre("CLIENTE")
            .orElseGet(() -> rolRepository.save(new Rol("CLIENTE")));

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRoles(Set.of(rolCliente));

        try {
            usuarioRepository.save(usuario);
        } catch (DataIntegrityViolationException ex) {
            // Dos registros simultáneos con el mismo correo
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El correo electrónico ya está registrado."));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("message", "¡Usuario registrado exitosamente!"));
    }

    // El frontend maneja un solo rol por usuario: "admin" o "cliente"
    private String rolUnico(Usuario usuario) {
        return usuario.getRoles().stream().anyMatch(r -> r.getNombre().equals("ADMIN"))
            ? "admin" : "cliente";
    }
}