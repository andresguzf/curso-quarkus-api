package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDate;

// Query params de GET /api/users: página, orden y filtros opcionales. email se busca como "contiene"
// sin distinguir mayúsculas; createdFrom y createdTo filtran por fecha de alta, ambos incluidos
public record UserFilter(
        @QueryParam("page") @DefaultValue("0")
        @Min(value = 0, message = "La página no puede ser negativa")
        int page,
        @QueryParam("size") @DefaultValue("10")
        @Min(value = 1, message = "El tamaño de página debe ser al menos {value}")
        @Max(value = 100, message = "El tamaño de página no puede ser mayor que {value}")
        int size,
        // Lista blanca: el campo de orden nunca se arma con texto libre del usuario
        @QueryParam("sort") @DefaultValue("createdAt")
        @Pattern(regexp = "email|role|createdAt", message = "Solo se puede ordenar por email, role o createdAt")
        String sort,
        @QueryParam("dir") @DefaultValue("desc")
        @Pattern(regexp = "asc|desc", message = "La dirección debe ser asc o desc")
        String dir,
        @QueryParam("email") String email,
        @QueryParam("role")
        @Pattern(regexp = "user|admin", message = "El rol debe ser user o admin")
        String role,
        @QueryParam("createdFrom") LocalDate createdFrom,
        @QueryParam("createdTo") LocalDate createdTo) {
}
