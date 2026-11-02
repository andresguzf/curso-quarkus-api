package com.andres.course.claude.quarkus.quarkusapi.app.security;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.UserRepository;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Crea el primer admin al arrancar, con ADMIN_EMAIL y ADMIN_PASSWORD (por ejemplo desde el .env),
 * solo si todavía no existe ningún admin. Sin esas variables no hace nada.
 */
@ApplicationScoped
public class AdminSeeder {

    private final UserRepository repository;
    private final PasswordHasher passwordHasher;
    private final Optional<String> email;
    private final Optional<String> password;

    public AdminSeeder(UserRepository repository, PasswordHasher passwordHasher,
                       @ConfigProperty(name = "app.auth.admin.email") Optional<String> email,
                       @ConfigProperty(name = "app.auth.admin.password") Optional<String> password) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.email = email;
        this.password = password;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (email.isEmpty() || password.isEmpty() || repository.countByRole(Role.ADMIN) > 0) {
            return;
        }
        if (repository.existsByEmail(email.get())) {
            Log.warnf("No se creó el admin inicial: el email %s ya pertenece a otro usuario", email.get());
            return;
        }
        User admin = new User();
        admin.setEmail(email.get());
        admin.setPassword(passwordHasher.hash(password.get()));
        admin.setRole(Role.ADMIN);
        repository.persist(admin);
        Log.infof("Admin inicial creado: %s", email.get());
    }
}
