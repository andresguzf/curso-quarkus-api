package com.andres.course.claude.quarkus.quarkusapi.app.services;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Customer;
import com.andres.course.claude.quarkus.quarkusapi.app.mappers.CustomerMapper;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerDto;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.CustomerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public CustomerServiceImpl(CustomerRepository repository, CustomerMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<CustomerDto> findAll() {
        return repository.listAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public CustomerDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Override
    public CustomerDto findByEmail(String email) {
        return repository.findByEmail(email)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("Customer con email " + email + " no existe"));
    }

    @Override
    public List<CustomerDto> searchByName(String term) {
        return repository.searchByName(term).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public CustomerDto create(CustomerDto dto) {
        if (repository.existsByEmail(dto.email())) {
            throw emailInUse(dto.email());
        }
        Customer entity = mapper.toEntity(dto);
        repository.persist(entity);
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public CustomerDto update(Long id, CustomerDto dto) {
        Customer entity = getOrThrow(id);
        // El email puede repetirse solo si es el del mismo cliente
        repository.findByEmail(dto.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw emailInUse(dto.email());
                });
        mapper.updateEntity(entity, dto);
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.deleteById(id)) {
            throw new NotFoundException("Customer " + id + " no existe");
        }
    }

    private Customer getOrThrow(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " no existe"));
    }

    private ClientErrorException emailInUse(String email) {
        return new ClientErrorException("El email " + email + " ya está registrado", Response.Status.CONFLICT);
    }
}
