package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.PageDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserFilter;
import com.andres.course.claude.quarkus.quarkusapi.app.services.UserService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;

// Administración de usuarios: solo admin
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Role.Names.ADMIN)
@Tag(name = "Users")
public class UserController {

    private final UserService service;
    private final SecurityIdentity identity;

    public UserController(UserService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @GET
    @Operation(summary = "Lista los usuarios paginados, con orden y filtros opcionales")
    public PageDto<UserDto> findAll(@Valid @BeanParam UserFilter filter) {
        return service.search(filter);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Busca un usuario por id")
    public UserDto findById(@PathParam("id") Long id) {
        return service.findById(id);
    }

    @POST
    @Operation(summary = "Crea un usuario con el rol indicado (user por defecto)")
    public Response create(@Valid @ConvertGroup(from = Default.class, to = UserDto.OnCreate.class)
                           @NotNull(message = "Envía los datos del usuario") UserDto user) {
        UserDto created = service.create(user);
        return Response.created(URI.create("/api/users/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualiza un usuario; sin password se conserva el actual")
    public UserDto update(@PathParam("id") Long id,
                          @Valid @NotNull(message = "Envía los datos del usuario") UserDto user) {
        return service.update(id, user);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Elimina un usuario")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id, identity.getPrincipal().getName());
        return Response.noContent().build();
    }
}
