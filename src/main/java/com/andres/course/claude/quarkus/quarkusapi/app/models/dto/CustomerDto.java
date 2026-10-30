package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerDto(
        Long id,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede tener más de {max} caracteres")
        String name,
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede tener más de {max} caracteres")
        String lastName,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede tener más de {max} caracteres")
        String email) {
}
