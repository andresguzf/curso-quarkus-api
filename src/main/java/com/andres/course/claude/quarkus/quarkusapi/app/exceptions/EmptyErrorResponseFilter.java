package com.andres.course.claude.quarkus.quarkusapi.app.exceptions;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.Map;

/**
 * Completa con un ErrorDto las respuestas de error que llegan sin body y que no pasan por
 * GlobalExceptionMapper. El caso principal es el filtro CSRF de Quarkus: cuando falta la cabecera
 * X-CSRF-TOKEN o no coincide con la cookie, corta la petición con un 400 vacío.
 */
public class EmptyErrorResponseFilter {

    private static final String CSRF_MESSAGE =
            "No pudimos verificar la solicitud: falta el token CSRF o no es válido. "
                    + "Pide uno nuevo en /api/auth/csrf y envíalo en la cabecera X-CSRF-TOKEN.";

    @ServerResponseFilter
    public void fillEmptyError(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400 || response.hasEntity()) {
            return;
        }
        String message = status == 400 ? CSRF_MESSAGE : GlobalExceptionMapper.defaultMessage(status);
        response.setEntity(GlobalExceptionMapper.errorBody(status, message, request.getUriInfo().getPath(), Map.of()),
                null, MediaType.APPLICATION_JSON_TYPE);
    }
}
