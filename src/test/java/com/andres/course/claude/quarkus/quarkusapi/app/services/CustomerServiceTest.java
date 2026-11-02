package com.andres.course.claude.quarkus.quarkusapi.app.services;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Customer;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerFilter;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.PageDto;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.CustomerRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Usa PostgreSQL real levantado por Dev Services; cada test hace rollback al terminar
@QuarkusTest
@TestTransaction
class CustomerServiceTest {

    @Inject
    CustomerService service;

    @Inject
    CustomerRepository repository;

    @Test
    void createPersistsCustomerAndFillsCreatedAt() {
        CustomerDto created = service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        assertNotNull(created.id());
        assertEquals("Ana", created.name());
        assertEquals("Pérez", created.lastName());
        assertEquals("ana@ejemplo.com", created.email());

        Customer entity = repository.findById(created.id());
        assertNotNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    void createWithDuplicateEmailReturnsConflict() {
        service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.create(newCustomer("Otra", "Persona", "ana@ejemplo.com")));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void searchReturnsPageWithTotals() {
        service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));
        service.create(newCustomer("Luis", "Soto", "luis@ejemplo.com"));
        service.create(newCustomer("Marta", "Gómez", "marta@ejemplo.com"));

        PageDto<CustomerDto> page = service.search(
                new CustomerFilter(0, 2, "name", "asc", null, null, null, null, null));

        assertEquals(List.of("Ana", "Luis"), page.content().stream().map(CustomerDto::name).toList());
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertNotNull(page.content().getFirst().createdAt());
    }

    @Test
    void searchFiltersByNameIgnoringCase() {
        service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));
        service.create(newCustomer("Luis", "Soto", "luis@ejemplo.com"));

        PageDto<CustomerDto> page = service.search(
                new CustomerFilter(0, 10, "createdAt", "desc", "LU", null, null, null, null));

        assertEquals(List.of("Luis"), page.content().stream().map(CustomerDto::name).toList());
    }

    @Test
    void findByIdReturnsCustomer() {
        CustomerDto created = service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        assertEquals(created, service.findById(created.id()));
    }

    @Test
    void findByIdNotFoundThrows404() {
        assertThrows(NotFoundException.class, () -> service.findById(999_999L));
    }

    @Test
    void findByEmailReturnsCustomer() {
        CustomerDto created = service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        assertEquals(created, service.findByEmail("ana@ejemplo.com"));
        assertThrows(NotFoundException.class, () -> service.findByEmail("nadie@ejemplo.com"));
    }

    @Test
    void searchByNameMatchesNameAndLastNameIgnoringCase() {
        service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));
        service.create(newCustomer("Luis", "Anabalón", "luis@ejemplo.com"));
        service.create(newCustomer("Pedro", "Soto", "pedro@ejemplo.com"));

        List<CustomerDto> result = service.searchByName("ANA");

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(c -> c.name().equals("Pedro")));
    }

    @Test
    void updateChangesFieldsAndFillsUpdatedAt() {
        CustomerDto created = service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        CustomerDto updated = service.update(created.id(), newCustomer("Ana María", "Pérez", "ana.maria@ejemplo.com"));
        // Fuerza el UPDATE para que se dispare @PreUpdate dentro de la transacción del test
        repository.flush();

        assertEquals(created.id(), updated.id());
        assertEquals("Ana María", updated.name());
        assertEquals("ana.maria@ejemplo.com", updated.email());
        assertNotNull(repository.findById(created.id()).getUpdatedAt());
    }

    @Test
    void updateKeepingOwnEmailIsAllowed() {
        CustomerDto created = service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        CustomerDto updated = service.update(created.id(), newCustomer("Ana", "Rojas", "ana@ejemplo.com"));

        assertEquals("Rojas", updated.lastName());
    }

    @Test
    void updateWithEmailOfAnotherCustomerReturnsConflict() {
        service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));
        CustomerDto luis = service.create(newCustomer("Luis", "Soto", "luis@ejemplo.com"));

        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.update(luis.id(), newCustomer("Luis", "Soto", "ana@ejemplo.com")));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void updateNotFoundThrows404() {
        assertThrows(NotFoundException.class,
                () -> service.update(999_999L, newCustomer("Ana", "Pérez", "ana@ejemplo.com")));
    }

    @Test
    void deleteRemovesCustomer() {
        CustomerDto created = service.create(newCustomer("Ana", "Pérez", "ana@ejemplo.com"));

        service.delete(created.id());

        assertFalse(repository.findByIdOptional(created.id()).isPresent());
    }

    @Test
    void deleteNotFoundThrows404() {
        assertThrows(NotFoundException.class, () -> service.delete(999_999L));
    }

    private static CustomerDto newCustomer(String name, String lastName, String email) {
        return new CustomerDto(null, name, lastName, email);
    }
}
