package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// createdAt y updatedAt son de solo lectura: se devuelven para mostrarlas y filtrar, pero se ignoran al recibir
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
        String email,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        LocalDateTime createdAt,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        LocalDateTime updatedAt) {

    // Sin fechas de auditoría: las completa la entity al guardar
    public CustomerDto(Long id, String name, String lastName, String email) {
        this(id, name, lastName, email, null, null);
    }
}
