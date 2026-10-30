package com.andres.course.claude.quarkus.quarkusapi.app.exceptions;

import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.ErrorDto;
import com.fasterxml.jackson.core.JacksonException;
import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Manejo global de excepciones: convierte cualquier error de la API en un {@link ErrorDto} JSON con un
 * mensaje simple para el usuario final. Quarkus elige el método cuyo tipo de excepción sea el más específico.
 */
public class GlobalExceptionMapper {

    /**
     * 400: datos que no cumplen las validaciones de Bean Validation ({@code @Valid}, {@code @NotBlank}...).
     * {@code errors} agrupa los mensajes por campo, ordenados por nombre: {"email": ["...", "..."]}.
     */
    @ServerExceptionMapper
    public Response handleValidation(ConstraintViolationException exception, UriInfo uriInfo) {
        Map<String, List<String>> errors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(GlobalExceptionMapper::fieldName, TreeMap::new,
                        Collectors.mapping(ConstraintViolation::getMessage,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        messages -> messages.stream().sorted().toList()))));
        return build(400, "Algunos datos no son válidos. Revisa los campos e inténtalo de nuevo.", uriInfo, errors);
    }

    /**
     * 400: el cuerpo de la petición no es un JSON válido o un campo tiene un tipo incorrecto.
     */
    @ServerExceptionMapper
    public Response handleInvalidJson(JacksonException exception, UriInfo uriInfo) {
        Log.debugf("JSON inválido en %s: %s", uriInfo.getPath(), exception.getOriginalMessage());
        return build(400, "El cuerpo de la solicitud no es un JSON válido. Revisa el formato e inténtalo de nuevo.",
                uriInfo, Map.of());
    }

    /**
     * Errores HTTP lanzados por los services ({@code NotFoundException} 404, {@code ClientErrorException} 409...)
     * y por Quarkus REST (ruta inexistente, método no permitido...). Si la excepción trae un mensaje propio se
     * usa; si no, se usa uno genérico según el status.
     */
    @ServerExceptionMapper
    public Response handleHttpError(WebApplicationException exception, UriInfo uriInfo) {
        // Quarkus REST envuelve el error de Jackson en un 400 cuando no puede leer el body
        if (exception.getCause() instanceof JacksonException jacksonException) {
            return handleInvalidJson(jacksonException, uriInfo);
        }
        int status = exception.getResponse().getStatus();
        String message = exception.getMessage();
        // Las excepciones sin mensaje propio traen el texto técnico "HTTP 404 Not Found"
        if (message == null || message.isBlank() || message.startsWith("HTTP ")) {
            message = defaultMessage(status);
        }
        return build(status, message, uriInfo, Map.of());
    }

    /**
     * 500: cualquier error no previsto. El detalle queda en el log y al usuario solo le llega un mensaje simple.
     */
    @ServerExceptionMapper
    public Response handleUnexpected(Exception exception, UriInfo uriInfo) {
        Log.errorf(exception, "Error inesperado en %s", uriInfo.getPath());
        return build(500, "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.", uriInfo, Map.of());
    }

    private static Response build(int status, String message, UriInfo uriInfo, Map<String, List<String>> errors) {
        ErrorDto body = new ErrorDto(status, title(status), message, "/" + uriInfo.getPath().replaceFirst("^/", ""),
                LocalDateTime.now(), errors);
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    // El último nodo del path de la violación es el campo (create.dto.email -> email); si es el parámetro
    // completo (por ejemplo el body vacío) se usa su nombre
    private static String fieldName(ConstraintViolation<?> violation) {
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            field = node.getName();
        }
        return field;
    }

    private static String title(int status) {
        return switch (status) {
            case 400 -> "Solicitud inválida";
            case 401 -> "No autenticado";
            case 403 -> "Acceso denegado";
            case 404 -> "No encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato no disponible";
            case 409 -> "Conflicto";
            case 415 -> "Formato no soportado";
            default -> status >= 500 ? "Error interno" : "Error en la solicitud";
        };
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 400 -> "La solicitud no es válida. Revisa los datos e inténtalo de nuevo.";
            case 404 -> "La ruta o el recurso que buscas no existe. Revisa la URL e inténtalo de nuevo.";
            case 405 -> "Esta operación no está permitida para esta ruta.";
            case 406 -> "No podemos responder en el formato solicitado. Usa application/json.";
            case 415 -> "El formato enviado no es compatible. Envía los datos como application/json.";
            default -> status >= 500
                    ? "Ocurrió un error inesperado. Inténtalo de nuevo más tarde."
                    : "No pudimos procesar la solicitud. Revisa los datos e inténtalo de nuevo.";
        };
    }
}
