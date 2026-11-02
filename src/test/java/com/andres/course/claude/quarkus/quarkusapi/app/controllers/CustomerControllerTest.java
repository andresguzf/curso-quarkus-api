package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Customer;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.CustomerRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

// Cada request HTTP corre en su propia transacción y confirma sus cambios, así que se limpia la
// tabla antes y después de cada test para no afectar a otras clases de test
// Por defecto cada test corre como admin; los casos del rol user lo cambian con su propio @TestSecurity
@QuarkusTest
@TestSecurity(user = "admin@ejemplo.com", roles = "admin")
class CustomerControllerTest {

    private static final String PATH = "/api/customers";

    @Inject
    CustomerRepository repository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        QuarkusTransaction.requiringNew().run(repository::deleteAll);
    }

    @Test
    void createReturns201WithLocationAndBody() {
        var response = given().contentType(ContentType.JSON)
                .body(json("Ana", "Pérez", "ana@ejemplo.com"))
                .when().post(PATH)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("name", is("Ana"))
                .body("lastName", is("Pérez"))
                .body("email", is("ana@ejemplo.com"))
                // Las fechas de auditoría se devuelven: createdAt al crear, updatedAt recién al editar
                .body("createdAt", notNullValue())
                .body("updatedAt", nullValue())
                .extract();

        long id = response.jsonPath().getLong("id");
        assertThat(response.header("Location"), endsWith(PATH + "/" + id));
    }

    @Test
    void createWithInvalidDataReturns400WithFieldErrors() {
        given().contentType(ContentType.JSON)
                .body(json("", "Pérez", "no-es-un-email"))
                .when().post(PATH)
                .then().statusCode(400)
                .contentType(ContentType.JSON)
                .body("status", is(400))
                .body("error", is("Solicitud inválida"))
                .body("message", is("Algunos datos no son válidos. Revisa los campos e inténtalo de nuevo."))
                .body("path", is(PATH))
                .body("timestamp", notNullValue())
                .body("errors.keySet()", containsInAnyOrder("email", "name"))
                .body("errors.name", contains("El nombre es obligatorio"))
                .body("errors.email", contains("El email no tiene un formato válido"));
    }

    @Test
    void fieldWithSeveralFailuresListsAllItsMessages() {
        String longEmail = "a".repeat(150) + "@ejemplo";

        given().contentType(ContentType.JSON)
                .body(json("Ana", "Pérez", longEmail))
                .when().post(PATH)
                .then().statusCode(400)
                .body("errors.keySet()", contains("email"))
                .body("errors.email", containsInAnyOrder(
                        "El email no puede tener más de 150 caracteres",
                        "El email no tiene un formato válido"));
    }

    @Test
    void createWithoutBodyReturns400() {
        given().contentType(ContentType.JSON)
                .when().post(PATH)
                .then().statusCode(400)
                .body("errors.customer", contains("Envía los datos del cliente"));
    }

    @Test
    void createWithMalformedJsonReturns400() {
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"Ana\",")
                .when().post(PATH)
                .then().statusCode(400)
                .body("message", is("El cuerpo de la solicitud no es un JSON válido. Revisa el formato e inténtalo de nuevo."))
                .body("errors", nullValue());
    }

    @Test
    void createWithDuplicateEmailReturns409() {
        create("Ana", "Pérez", "ana@ejemplo.com");

        given().contentType(ContentType.JSON)
                .body(json("Otra", "Persona", "ana@ejemplo.com"))
                .when().post(PATH)
                .then().statusCode(409)
                .body("status", is(409))
                .body("error", is("Conflicto"))
                .body("message", is("Ya existe un cliente registrado con el email ana@ejemplo.com."));
    }

    @Test
    void findAllReturnsFirstPageNewestFirst() {
        create("Ana", "Pérez", "ana@ejemplo.com");
        create("Luis", "Soto", "luis@ejemplo.com");

        given().when().get(PATH)
                .then().statusCode(200)
                .body("content", hasSize(2))
                .body("content.name", contains("Luis", "Ana"))
                .body("page", is(0))
                .body("size", is(10))
                .body("totalElements", is(2))
                .body("totalPages", is(1));
    }

    @Test
    void findAllPaginatesAndSorts() {
        create("Ana", "Pérez", "ana@ejemplo.com");
        create("Luis", "Soto", "luis@ejemplo.com");
        create("Marta", "Gómez", "marta@ejemplo.com");

        given().queryParam("page", 1).queryParam("size", 2)
                .queryParam("sort", "lastName").queryParam("dir", "asc")
                .when().get(PATH)
                .then().statusCode(200)
                .body("content.lastName", contains("Soto"))
                .body("page", is(1))
                .body("totalElements", is(3))
                .body("totalPages", is(2));
    }

    @Test
    void findAllFiltersByTextAndCreationDate() {
        create("Ana", "Pérez", "ana@ejemplo.com");
        create("Luis", "Pereira", "luis@otro.com");
        create("Marta", "Gómez", "marta@ejemplo.com");
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        given().queryParam("lastName", "PEREI")
                .when().get(PATH)
                .then().statusCode(200)
                .body("content.name", contains("Luis"));
        given().queryParam("name", "AN").queryParam("email", "ejemplo")
                .when().get(PATH)
                .then().statusCode(200)
                .body("content.name", contains("Ana"));
        given().queryParam("name", "ma").queryParam("createdFrom", today).queryParam("createdTo", today)
                .when().get(PATH)
                .then().statusCode(200)
                .body("content.name", contains("Marta"));
        given().queryParam("createdTo", yesterday)
                .when().get(PATH)
                .then().statusCode(200)
                .body("content", hasSize(0))
                .body("totalPages", is(0));
    }

    @Test
    void findAllWithInvalidParamsReturns400() {
        given().queryParam("sort", "password").queryParam("dir", "up").queryParam("size", 500)
                .when().get(PATH)
                .then().statusCode(400)
                .body("errors.sort", contains("Solo se puede ordenar por name, lastName, email o createdAt"))
                .body("errors.dir", contains("La dirección debe ser asc o desc"))
                .body("errors.size", contains("El tamaño de página no puede ser mayor que 100"));
    }

    @Test
    void findByIdReturnsCustomerOr404() {
        long id = create("Ana", "Pérez", "ana@ejemplo.com");

        given().when().get(PATH + "/" + id)
                .then().statusCode(200)
                .body("email", is("ana@ejemplo.com"));

        given().when().get(PATH + "/999999")
                .then().statusCode(404)
                .body("error", is("No encontrado"))
                .body("message", is("No encontramos un cliente con el id 999999."))
                .body("path", is(PATH + "/999999"));
    }

    @Test
    void unknownRouteReturns404Json() {
        given().when().get("/api/no-existe")
                .then().statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", is("No encontrado"))
                .body("message", is("La ruta o el recurso que buscas no existe. Revisa la URL e inténtalo de nuevo."))
                .body("path", is("/api/no-existe"));
    }

    @Test
    void findByEmailReturnsCustomerOr404() {
        create("Ana", "Pérez", "ana@ejemplo.com");

        given().when().get(PATH + "/email/ana@ejemplo.com")
                .then().statusCode(200)
                .body("name", is("Ana"));

        given().when().get(PATH + "/email/nadie@ejemplo.com")
                .then().statusCode(404);
    }

    @Test
    void searchByNameMatchesNameAndLastName() {
        create("Ana", "Pérez", "ana@ejemplo.com");
        create("Luis", "Anabalón", "luis@ejemplo.com");
        create("Pedro", "Soto", "pedro@ejemplo.com");

        given().queryParam("term", "ana")
                .when().get(PATH + "/search")
                .then().statusCode(200)
                .body("name", containsInAnyOrder("Ana", "Luis"));
    }

    @Test
    void searchWithoutTermReturns400() {
        given().when().get(PATH + "/search")
                .then().statusCode(400)
                .body("errors.term", contains("Indica un texto para buscar"));
    }

    @Test
    void updateReturnsUpdatedCustomer() {
        long id = create("Ana", "Pérez", "ana@ejemplo.com");

        given().contentType(ContentType.JSON)
                .body(json("Ana María", "Rojas", "ana.maria@ejemplo.com"))
                .when().put(PATH + "/" + id)
                .then().statusCode(200)
                .body("id", is((int) id))
                .body("name", is("Ana María"))
                .body("lastName", is("Rojas"))
                .body("email", is("ana.maria@ejemplo.com"));
    }

    @Test
    void updateWithEmailOfAnotherCustomerReturns409() {
        create("Ana", "Pérez", "ana@ejemplo.com");
        long luis = create("Luis", "Soto", "luis@ejemplo.com");

        given().contentType(ContentType.JSON)
                .body(json("Luis", "Soto", "ana@ejemplo.com"))
                .when().put(PATH + "/" + luis)
                .then().statusCode(409);
    }

    @Test
    void updateNotFoundReturns404() {
        given().contentType(ContentType.JSON)
                .body(json("Ana", "Pérez", "ana@ejemplo.com"))
                .when().put(PATH + "/999999")
                .then().statusCode(404);
    }

    @Test
    void deleteReturns204AndThen404() {
        long id = create("Ana", "Pérez", "ana@ejemplo.com");

        given().when().delete(PATH + "/" + id)
                .then().statusCode(204);

        given().when().delete(PATH + "/" + id)
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "user@ejemplo.com", roles = "user")
    void userRoleCanListSearchAndRead() {
        long id = createAsAdmin("Ana", "Pérez", "ana@ejemplo.com");

        given().when().get(PATH).then().statusCode(200).body("content", hasSize(1));
        given().when().get(PATH + "/" + id).then().statusCode(200);
        given().queryParam("term", "ana").when().get(PATH + "/search").then().statusCode(200);
        given().when().get(PATH + "/email/ana@ejemplo.com").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "user@ejemplo.com", roles = "user")
    void userRoleCannotCreateUpdateOrDelete() {
        long id = createAsAdmin("Ana", "Pérez", "ana@ejemplo.com");

        given().contentType(ContentType.JSON).body(json("Luis", "Soto", "luis@ejemplo.com"))
                .when().post(PATH)
                .then().statusCode(403)
                .body("error", is("Acceso denegado"))
                .body("message", is("No tienes permisos para realizar esta acción."));
        given().contentType(ContentType.JSON).body(json("Ana", "Rojas", "ana@ejemplo.com"))
                .when().put(PATH + "/" + id)
                .then().statusCode(403);
        given().when().delete(PATH + "/" + id)
                .then().statusCode(403);
    }

    @Test
    void writeWithoutCsrfTokenReturns400() {
        RestAssured.given().contentType(ContentType.JSON)
                .body(json("Ana", "Pérez", "ana@ejemplo.com"))
                .when().post(PATH)
                .then().statusCode(400)
                .contentType(ContentType.JSON)
                .body("message", is("No pudimos verificar la solicitud: falta el token CSRF o no es válido. "
                        + "Pide uno nuevo en /api/auth/csrf y envíalo en la cabecera X-CSRF-TOKEN."));
    }

    // Todas las peticiones de esta clase llevan el token CSRF
    private static RequestSpecification given() {
        return Csrf.given();
    }

    // Inserta directo en la base, sin pasar por la seguridad del endpoint
    private long createAsAdmin(String name, String lastName, String email) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var customer = new Customer();
            customer.setName(name);
            customer.setLastName(lastName);
            customer.setEmail(email);
            repository.persist(customer);
            return customer.getId();
        });
    }

    private long create(String name, String lastName, String email) {
        return given().contentType(ContentType.JSON)
                .body(json(name, lastName, email))
                .when().post(PATH)
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static String json(String name, String lastName, String email) {
        return """
                {"name": "%s", "lastName": "%s", "email": "%s"}
                """.formatted(name, lastName, email);
    }
}
