package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import java.util.List;

// Una página de resultados: content trae solo los elementos de la página pedida; page empieza en 0
public record PageDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageDto<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageDto<>(content, page, size, totalElements, totalPages);
    }
}
