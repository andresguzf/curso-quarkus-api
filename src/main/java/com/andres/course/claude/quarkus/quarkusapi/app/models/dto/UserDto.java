package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

// password es de solo escritura: se recibe al registrar, crear o editar, pero nunca se devuelve en el JSON
public record UserDto(
        Long id,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede tener más de {max} caracteres")
        String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(groups = OnCreate.class, message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre {min} y {max} caracteres")
        String password,
        @Pattern(regexp = "user|admin", message = "El rol debe ser user o admin")
        String role) {

    // Validaciones de alta (registro y creación): además de las de Default, exige el password.
    // Al editar se valida solo Default: si password viene vacío se conserva el actual
    public interface OnCreate extends Default {
    }
}
