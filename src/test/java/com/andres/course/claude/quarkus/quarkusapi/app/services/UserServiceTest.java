package com.andres.course.claude.quarkus.quarkusapi.app.services;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.LoginDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.PageDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserFilter;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.UserRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// PostgreSQL real de Dev Services; cada test hace rollback al terminar
@QuarkusTest
@TestTransaction
class UserServiceTest {

    @Inject
    UserService service;

    @Inject
    UserRepository repository;

    @Test
    void registerAlwaysCreatesUserRoleAndHashesThePassword() {
        UserDto created = service.register(new UserDto(null, "ana@ejemplo.com", "secreto123", "admin"));

        assertEquals("user", created.role());
        assertNull(created.password());
        User entity = repository.findById(created.id());
        assertNotEquals("secreto123", entity.getPassword());
        assertTrue(entity.getPassword().startsWith("$2"), "el password debe guardarse como hash bcrypt");
    }

    @Test
    void createRespectsRoleAndRejectsDuplicateEmail() {
        assertEquals("admin", service.create(new UserDto(null, "admin@ejemplo.com", "secreto123", "admin")).role());
        assertEquals("user", service.create(new UserDto(null, "luis@ejemplo.com", "secreto123", null)).role());

        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.register(new UserDto(null, "luis@ejemplo.com", "otroSecreto1", null)));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void authenticateAcceptsValidCredentialsOnly() {
        service.register(new UserDto(null, "ana@ejemplo.com", "secreto123", null));

        assertEquals("ana@ejemplo.com", service.authenticate(new LoginDto("ana@ejemplo.com", "secreto123")).email());

        // Mismo mensaje si el password es incorrecto o si el email no existe
        NotAuthorizedException wrongPassword = assertThrows(NotAuthorizedException.class,
                () -> service.authenticate(new LoginDto("ana@ejemplo.com", "incorrecto")));
        NotAuthorizedException unknownEmail = assertThrows(NotAuthorizedException.class,
                () -> service.authenticate(new LoginDto("nadie@ejemplo.com", "secreto123")));
        assertEquals("Email o contraseña incorrectos.", wrongPassword.getMessage());
        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
    }

    @Test
    void updateWithoutPasswordKeepsTheCurrentHash() {
        UserDto created = service.register(new UserDto(null, "ana@ejemplo.com", "secreto123", null));
        String hash = repository.findById(created.id()).getPassword();

        service.update(created.id(), new UserDto(null, "ana.perez@ejemplo.com", null, "admin"));

        User entity = repository.findById(created.id());
        assertEquals("ana.perez@ejemplo.com", entity.getEmail());
        assertEquals(hash, entity.getPassword());
        assertEquals("admin", service.findById(created.id()).role());
    }

    @Test
    void updateWithPasswordChangesTheHash() {
        UserDto created = service.register(new UserDto(null, "ana@ejemplo.com", "secreto123", null));

        service.update(created.id(), new UserDto(null, "ana@ejemplo.com", "nuevoSecreto1", null));

        assertEquals("ana@ejemplo.com", service.authenticate(new LoginDto("ana@ejemplo.com", "nuevoSecreto1")).email());
    }

    @Test
    void cannotRemoveAdminRoleFromTheLastAdmin() {
        UserDto admin = service.create(new UserDto(null, "admin@ejemplo.com", "secreto123", "admin"));

        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.update(admin.id(), new UserDto(null, "admin@ejemplo.com", null, "user")));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void adminCannotDeleteItself() {
        UserDto admin = service.create(new UserDto(null, "admin@ejemplo.com", "secreto123", "admin"));
        service.create(new UserDto(null, "otro.admin@ejemplo.com", "secreto123", "admin"));

        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.delete(admin.id(), "admin@ejemplo.com"));
        assertEquals("No puedes eliminar tu propio usuario.", ex.getMessage());
    }

    @Test
    void cannotDeleteTheLastAdmin() {
        UserDto admin = service.create(new UserDto(null, "admin@ejemplo.com", "secreto123", "admin"));

        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.delete(admin.id(), "otro@ejemplo.com"));
        assertEquals("No puedes eliminar al único administrador.", ex.getMessage());
    }

    @Test
    void deleteRemovesTheUser() {
        service.create(new UserDto(null, "admin@ejemplo.com", "secreto123", "admin"));
        UserDto user = service.register(new UserDto(null, "ana@ejemplo.com", "secreto123", null));

        service.delete(user.id(), "admin@ejemplo.com");

        assertTrue(repository.findByIdOptional(user.id()).isEmpty());
    }

    @Test
    void searchFiltersByRole() {
        service.create(new UserDto(null, "ana@ejemplo.com", "secreto123", "user"));
        service.create(new UserDto(null, "luis@ejemplo.com", "secreto123", "admin"));

        PageDto<UserDto> page = service.search(
                new UserFilter(0, 10, "email", "asc", null, "admin", null, null));

        assertTrue(page.content().stream().allMatch(user -> "admin".equals(user.role())));
        assertTrue(page.content().stream().map(UserDto::email).toList().contains("luis@ejemplo.com"));
        assertEquals(page.totalElements(), repository.countByRole(Role.ADMIN));
    }
}
