package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import com.andres.course.claude.quarkus.quarkusapi.app.repositories.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

// Cada request confirma su transacción, así que se limpia la tabla antes y después de cada test
@QuarkusTest
@TestSecurity(user = "admin@ejemplo.com", roles = "admin")
class UserControllerTest {

    private static final String PATH = "/api/users";

    @Inject
    UserRepository repository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        QuarkusTransaction.requiringNew().run(repository::deleteAll);
    }

    @Test
    void createReturns201WithoutPassword() {
        given().contentType(ContentType.JSON)
                .body(json("ana@ejemplo.com", "secreto123", "admin"))
                .when().post(PATH)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("email", is("ana@ejemplo.com"))
                .body("role", is("admin"))
                .body("$", not(hasKey("password")));
    }

    @Test
    void createWithoutPasswordOrWithInvalidRoleReturns400() {
        given().contentType(ContentType.JSON)
                .body("{\"email\": \"ana@ejemplo.com\", \"role\": \"root\"}")
                .when().post(PATH)
                .then().statusCode(400)
                .body("errors.password", contains("La contraseña es obligatoria"))
                .body("errors.role", contains("El rol debe ser user o admin"));
    }

    @Test
    void listAndFindNeverExposeThePassword() {
        long id = create("ana@ejemplo.com", "secreto123", "user");
        create("luis@ejemplo.com", "secreto123", "admin");

        given().when().get(PATH)
                .then().statusCode(200)
                .body("content.email", containsInAnyOrder("ana@ejemplo.com", "luis@ejemplo.com"))
                .body("content.password", contains(null, null))
                .body("content.createdAt", contains(notNullValue(), notNullValue()));
        given().when().get(PATH + "/" + id)
                .then().statusCode(200)
                .body("$", not(hasKey("password")));
    }

    @Test
    void findAllFiltersByRoleAndEmail() {
        create("ana@ejemplo.com", "secreto123", "user");
        create("luis@ejemplo.com", "secreto123", "admin");
        create("marta@otro.com", "secreto123", "admin");

        given().queryParam("role", "admin").queryParam("sort", "email").queryParam("dir", "asc")
                .when().get(PATH)
                .then().statusCode(200)
                .body("content.email", contains("luis@ejemplo.com", "marta@otro.com"))
                .body("totalElements", is(2));
        given().queryParam("role", "admin").queryParam("email", "EJEMPLO")
                .when().get(PATH)
                .then().statusCode(200)
                .body("content.email", contains("luis@ejemplo.com"));
    }

    @Test
    void findAllWithInvalidRoleOrSortReturns400() {
        given().queryParam("role", "root").queryParam("sort", "password")
                .when().get(PATH)
                .then().statusCode(400)
                .body("errors.role", contains("El rol debe ser user o admin"))
                .body("errors.sort", contains("Solo se puede ordenar por email, role o createdAt"));
    }

    @Test
    void updateWithoutPasswordChangesEmailAndRole() {
        long id = create("ana@ejemplo.com", "secreto123", "user");

        given().contentType(ContentType.JSON)
                .body("{\"email\": \"ana.perez@ejemplo.com\", \"role\": \"admin\"}")
                .when().put(PATH + "/" + id)
                .then().statusCode(200)
                .body("email", is("ana.perez@ejemplo.com"))
                .body("role", is("admin"));
    }

    @Test
    void deleteReturns204AndAdminCannotDeleteItself() {
        long me = create("admin@ejemplo.com", "secreto123", "admin");
        long ana = create("ana@ejemplo.com", "secreto123", "user");

        given().when().delete(PATH + "/" + ana).then().statusCode(204);
        given().when().delete(PATH + "/" + me)
                .then().statusCode(409)
                .body("message", is("No puedes eliminar tu propio usuario."));
    }

    @Test
    @TestSecurity(user = "user@ejemplo.com", roles = "user")
    void userRoleCannotManageUsers() {
        given().when().get(PATH)
                .then().statusCode(403)
                .body("message", is("No tienes permisos para realizar esta acción."));
        given().contentType(ContentType.JSON)
                .body(json("ana@ejemplo.com", "secreto123", "admin"))
                .when().post(PATH)
                .then().statusCode(403);
    }

    private long create(String email, String password, String role) {
        return given().contentType(ContentType.JSON)
                .body(json(email, password, role))
                .when().post(PATH)
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static RequestSpecification given() {
        return Csrf.given();
    }

    private static String json(String email, String password, String role) {
        return """
                {"email": "%s", "password": "%s", "role": "%s"}
                """.formatted(email, password, role);
    }
}
