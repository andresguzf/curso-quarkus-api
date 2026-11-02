package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.LoginDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import com.andres.course.claude.quarkus.quarkusapi.app.security.TokenService;
import com.andres.course.claude.quarkus.quarkusapi.app.services.UserService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;

/**
 * Registro, login y logout. La sesión es un JWT guardado en la cookie HttpOnly access_token; las peticiones
 * que modifican datos (incluidos login y registro) deben enviar el token CSRF en la cabecera X-CSRF-TOKEN.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth")
public class AuthController {

    private final UserService service;
    private final TokenService tokenService;
    private final SecurityIdentity identity;
    private final String cookieName;
    private final boolean cookieSecure;

    public AuthController(UserService service, TokenService tokenService, SecurityIdentity identity,
                          @ConfigProperty(name = "mp.jwt.token.cookie") String cookieName,
                          @ConfigProperty(name = "app.auth.cookie-secure") boolean cookieSecure) {
        this.service = service;
        this.tokenService = tokenService;
        this.identity = identity;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    @GET
    @Path("/csrf")
    @PermitAll
    @Operation(summary = "Entrega la cookie csrf-token; su valor se envía en la cabecera X-CSRF-TOKEN")
    public Response csrf() {
        return Response.noContent().build();
    }

    @POST
    @Path("/register")
    @PermitAll
    @Operation(summary = "Registra un usuario con rol user")
    public Response register(@Valid @ConvertGroup(from = Default.class, to = UserDto.OnCreate.class)
                             @NotNull(message = "Envía los datos del usuario") UserDto user) {
        UserDto created = service.register(user);
        return Response.created(URI.create("/api/users/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Inicia sesión y deja el JWT en la cookie HttpOnly")
    public Response login(@Valid @NotNull(message = "Envía el email y la contraseña") LoginDto credentials) {
        UserDto user = service.authenticate(credentials);
        String token = tokenService.generate(user);
        return Response.ok(user).cookie(sessionCookie(token, (int) tokenService.duration().toSeconds())).build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    // Sin body: no exige Content-Type JSON
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Cierra la sesión borrando la cookie")
    public Response logout() {
        return Response.noContent().cookie(sessionCookie("", 0)).build();
    }

    @GET
    @Path("/me")
    @Authenticated
    @Operation(summary = "Devuelve el usuario de la sesión")
    public UserDto me() {
        return service.findByEmail(identity.getPrincipal().getName());
    }

    // HttpOnly: el JavaScript del navegador no puede leer el token. SameSite=Strict: el navegador no la envía
    // en peticiones iniciadas desde otros sitios. Secure en prod, para enviarla solo por HTTPS
    private NewCookie sessionCookie(String value, int maxAge) {
        return new NewCookie.Builder(cookieName)
                .value(value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }
}
