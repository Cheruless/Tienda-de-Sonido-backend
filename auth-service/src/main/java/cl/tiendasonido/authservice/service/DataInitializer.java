package cl.tiendasonido.authservice.service;

import cl.tiendasonido.authservice.entity.Rol;
import cl.tiendasonido.authservice.entity.Usuario;
import cl.tiendasonido.authservice.repository.RolRepository;
import cl.tiendasonido.authservice.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RolRepository rolRepository,
                           UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Rol admin = rolRepository.findByNombre("ADMIN")
                .orElseGet(() -> rolRepository.save(new Rol("ADMIN")));
        Rol cliente = rolRepository.findByNombre("CLIENTE")
                .orElseGet(() -> rolRepository.save(new Rol("CLIENTE")));

        crearSiNoExiste("admin", "admin@tiendasonido.cl", "admin123", admin);
        crearSiNoExiste("cliente", "cliente@tiendasonido.cl", "cliente123", cliente);
    }

    private void crearSiNoExiste(String username, String email, String rawPassword, Rol rol) {
        if (usuarioRepository.existsByUsername(username)) return;
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRoles(Set.of(rol));
        usuarioRepository.save(u);
    }
}