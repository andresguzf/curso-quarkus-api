package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Respuesta de error común a toda la API. errors solo aparece en los errores de validación:
// cada campo que falló con la lista de sus mensajes, por ejemplo {"email": ["El email es obligatorio"]}
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorDto(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, List<String>> errors) {
}
