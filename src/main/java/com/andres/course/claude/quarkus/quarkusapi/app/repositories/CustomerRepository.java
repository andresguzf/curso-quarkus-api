package com.andres.course.claude.quarkus.quarkusapi.app.repositories;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Customer;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerFilter;
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
public class CustomerRepository implements PanacheRepository<Customer> {

    public Optional<Customer> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    public List<Customer> searchByName(String term) {
        return list("lower(name) like ?1 or lower(lastName) like ?1", "%" + term.toLowerCase() + "%");
    }

    // Arma la consulta solo con los filtros que llegan; los valores van como parámetros, nunca concatenados.
    // createdTo incluye todo ese día: se compara contra el inicio del día siguiente
    public PanacheQuery<Customer> search(CustomerFilter filter) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        addContains(conditions, params, "name", filter.name());
        addContains(conditions, params, "lastName", filter.lastName());
        addContains(conditions, params, "email", filter.email());
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
        PanacheQuery<Customer> query = conditions.isEmpty()
                ? findAll(sort)
                : find(String.join(" and ", conditions), sort, params);
        return query.page(Page.of(filter.page(), filter.size()));
    }

    private static void addContains(List<String> conditions, Map<String, Object> params, String field, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add("lower(" + field + ") like :" + field);
            params.put(field, "%" + value.trim().toLowerCase() + "%");
        }
    }
}
