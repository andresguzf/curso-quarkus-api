package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDate;

// Query params de GET /api/customers: página, orden y filtros opcionales. Los textos se buscan como
// "contiene" sin distinguir mayúsculas; createdFrom y createdTo filtran por fecha de alta, ambos incluidos
public record CustomerFilter(
        @QueryParam("page") @DefaultValue("0")
        @Min(value = 0, message = "La página no puede ser negativa")
        int page,
        @QueryParam("size") @DefaultValue("10")
        @Min(value = 1, message = "El tamaño de página debe ser al menos {value}")
        @Max(value = 100, message = "El tamaño de página no puede ser mayor que {value}")
        int size,
        // Lista blanca: el campo de orden nunca se arma con texto libre del usuario
        @QueryParam("sort") @DefaultValue("createdAt")
        @Pattern(regexp = "name|lastName|email|createdAt",
                message = "Solo se puede ordenar por name, lastName, email o createdAt")
        String sort,
        @QueryParam("dir") @DefaultValue("desc")
        @Pattern(regexp = "asc|desc", message = "La dirección debe ser asc o desc")
        String dir,
        @QueryParam("name") String name,
        @QueryParam("lastName") String lastName,
        @QueryParam("email") String email,
        @QueryParam("createdFrom") LocalDate createdFrom,
        @QueryParam("createdTo") LocalDate createdTo) {
}
