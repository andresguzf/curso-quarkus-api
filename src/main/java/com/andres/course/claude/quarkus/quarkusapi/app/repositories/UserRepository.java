package com.andres.course.claude.quarkus.quarkusapi.app.repositories;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    public long countByRole(Role role) {
        return count("role", role);
    }
}
