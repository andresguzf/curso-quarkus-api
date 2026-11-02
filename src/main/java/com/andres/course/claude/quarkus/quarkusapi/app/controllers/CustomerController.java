package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerFilter;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.PageDto;
import com.andres.course.claude.quarkus.quarkusapi.app.services.CustomerService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;

@Path("/api/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customers")
// Consultar: user y admin. Crear, editar y eliminar: solo admin (anotado en cada método)
@RolesAllowed({Role.Names.USER, Role.Names.ADMIN})
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GET
    @Operation(summary = "Lista los clientes paginados, con orden y filtros opcionales")
    public PageDto<CustomerDto> findAll(@Valid @BeanParam CustomerFilter filter) {
        return service.search(filter);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Busca un cliente por id")
    public CustomerDto findById(@PathParam("id") Long id) {
        return service.findById(id);
    }

    @GET
    @Path("/email/{email}")
    @Operation(summary = "Busca un cliente por email")
    public CustomerDto findByEmail(@PathParam("email") String email) {
        return service.findByEmail(email);
    }

    @GET
    @Path("/search")
    @Operation(summary = "Busca clientes por nombre o apellido, sin distinguir mayúsculas")
    public List<CustomerDto> searchByName(@QueryParam("term") @NotBlank(message = "Indica un texto para buscar") String term) {
        return service.searchByName(term);
    }

    @POST
    @RolesAllowed(Role.Names.ADMIN)
    @Operation(summary = "Crea un cliente")
    public Response create(@Valid @NotNull(message = "Envía los datos del cliente") CustomerDto customer) {
        CustomerDto created = service.create(customer);
        return Response.created(URI.create("/api/customers/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed(Role.Names.ADMIN)
    @Operation(summary = "Actualiza un cliente")
    public CustomerDto update(@PathParam("id") Long id,
                              @Valid @NotNull(message = "Envía los datos del cliente") CustomerDto customer) {
        return service.update(id, customer);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(Role.Names.ADMIN)
    @Operation(summary = "Elimina un cliente")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
