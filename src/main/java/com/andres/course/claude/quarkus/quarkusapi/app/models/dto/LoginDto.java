package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import jakarta.validation.constraints.NotBlank;

// Credenciales del login; no es un recurso, por eso va aparte de UserDto
public record LoginDto(
        @NotBlank(message = "El email es obligatorio")
        String email,
        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
