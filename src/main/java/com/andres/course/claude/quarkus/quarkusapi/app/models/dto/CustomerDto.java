package com.andres.course.claude.quarkus.quarkusapi.app.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerDto(
        Long id,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 150) String email) {
}
