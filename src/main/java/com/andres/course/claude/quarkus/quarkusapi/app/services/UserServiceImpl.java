package com.andres.course.claude.quarkus.quarkusapi.app.services;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import com.andres.course.claude.quarkus.quarkusapi.app.mappers.UserMapper;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.LoginDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.UserRepository;
import com.andres.course.claude.quarkus.quarkusapi.app.security.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordHasher passwordHasher;

    public UserServiceImpl(UserRepository repository, UserMapper mapper, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.mapper = mapper;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public List<UserDto> findAll() {
        return repository.listAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public UserDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Override
    public UserDto findByEmail(String email) {
        return repository.findByEmail(email)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("No encontramos un usuario con el email " + email + "."));
    }

    @Override
    @Transactional
    public UserDto create(UserDto dto) {
        if (repository.existsByEmail(dto.email())) {
            throw emailInUse(dto.email());
        }
        User entity = mapper.toEntity(dto);
        entity.setPassword(passwordHasher.hash(dto.password()));
        repository.persist(entity);
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public UserDto register(UserDto dto) {
        // En el registro público el rol lo decide la API, no quien se registra
        return create(new UserDto(null, dto.email(), dto.password(), Role.USER.group()));
    }

    @Override
    @Transactional
    public UserDto update(Long id, UserDto dto) {
        User entity = getOrThrow(id);
        repository.findByEmail(dto.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw emailInUse(dto.email());
                });
        // No se puede dejar el sistema sin admins
        if (entity.getRole() == Role.ADMIN && Role.fromGroup(dto.role()) == Role.USER
                && repository.countByRole(Role.ADMIN) == 1) {
            throw conflict("No puedes quitarle el rol admin al único administrador.");
        }
        mapper.updateEntity(entity, dto);
        // Sin password se conserva el actual
        if (dto.password() != null && !dto.password().isBlank()) {
            entity.setPassword(passwordHasher.hash(dto.password()));
        }
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, String currentEmail) {
        User entity = getOrThrow(id);
        if (entity.getEmail().equals(currentEmail)) {
            throw conflict("No puedes eliminar tu propio usuario.");
        }
        if (entity.getRole() == Role.ADMIN && repository.countByRole(Role.ADMIN) == 1) {
            throw conflict("No puedes eliminar al único administrador.");
        }
        repository.delete(entity);
    }

    @Override
    public UserDto authenticate(LoginDto credentials) {
        Optional<User> user = repository.findByEmail(credentials.email());
        if (user.isEmpty()) {
            // Mismo tiempo de respuesta que con un email existente, para no revelar qué emails están registrados
            passwordHasher.simulateMatch(credentials.password());
            throw invalidCredentials();
        }
        if (!passwordHasher.matches(credentials.password(), user.get().getPassword())) {
            throw invalidCredentials();
        }
        return mapper.toDto(user.get());
    }

    private User getOrThrow(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("No encontramos un usuario con el id " + id + "."));
    }

    private ClientErrorException emailInUse(String email) {
        return conflict("Ya existe un usuario registrado con el email " + email + ".");
    }

    private ClientErrorException conflict(String message) {
        return new ClientErrorException(message, Response.Status.CONFLICT);
    }

    // Mismo mensaje si el email no existe o el password es incorrecto
    private NotAuthorizedException invalidCredentials() {
        return new NotAuthorizedException("Email o contraseña incorrectos.", Response.status(Response.Status.UNAUTHORIZED).build());
    }
}
