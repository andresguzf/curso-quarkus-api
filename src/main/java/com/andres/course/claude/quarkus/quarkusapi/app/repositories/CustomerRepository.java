package com.andres.course.claude.quarkus.quarkusapi.app.repositories;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Customer;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
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
}
