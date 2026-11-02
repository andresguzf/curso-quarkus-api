package com.andres.course.claude.quarkus.quarkusapi.app.services;

import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.LoginDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.PageDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserFilter;

public interface UserService {

    // Página de usuarios según los filtros y el orden pedidos
    PageDto<UserDto> search(UserFilter filter);

    UserDto findById(Long id);

    UserDto findByEmail(String email);

    // Alta por un admin: respeta el rol enviado (user por defecto)
    UserDto create(UserDto dto);

    // Registro público: siempre crea el rol user
    UserDto register(UserDto dto);

    UserDto update(Long id, UserDto dto);

    // currentEmail es el admin que hace la petición: no puede eliminarse a sí mismo
    void delete(Long id, String currentEmail);

    // Devuelve el usuario si las credenciales son correctas; si no, 401 con un mensaje genérico
    UserDto authenticate(LoginDto credentials);
}
