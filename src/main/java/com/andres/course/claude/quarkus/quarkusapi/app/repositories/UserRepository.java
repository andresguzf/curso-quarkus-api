package com.andres.course.claude.quarkus.quarkusapi.app.repositories;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserFilter;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Arma la consulta solo con los filtros que llegan; los valores van como parámetros, nunca concatenados.
    // createdTo incluye todo ese día: se compara contra el inicio del día siguiente
    public PanacheQuery<User> search(UserFilter filter) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.email() != null && !filter.email().isBlank()) {
            conditions.add("lower(email) like :email");
            params.put("email", "%" + filter.email().trim().toLowerCase() + "%");
        }
        if (filter.role() != null) {
            conditions.add("role = :role");
            params.put("role", Role.fromGroup(filter.role()));
        }
        if (filter.createdFrom() != null) {
            conditions.add("createdAt >= :createdFrom");
            params.put("createdFrom", filter.createdFrom().atStartOfDay());
        }
        if (filter.createdTo() != null) {
            conditions.add("createdAt < :createdTo");
            params.put("createdTo", filter.createdTo().plusDays(1).atStartOfDay());
        }
        // El id desempata para que el orden sea estable entre páginas
        Sort.Direction direction = "asc".equals(filter.dir()) ? Sort.Direction.Ascending : Sort.Direction.Descending;
        Sort sort = Sort.by(filter.sort(), direction).and("id", direction);
        PanacheQuery<User> query = conditions.isEmpty()
                ? findAll(sort)
                : find(String.join(" and ", conditions), sort, params);
        return query.page(Page.of(filter.page(), filter.size()));
    }
}
