package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.CustomerRepository;
import com.andres.course.claude.quarkus.quarkusapi.app.repositories.UserRepository;
import com.andres.course.claude.quarkus.quarkusapi.app.security.PasswordHasher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

// Flujo real, sin @TestSecurity: CSRF, registro, login con cookie HttpOnly y permisos por rol
@QuarkusTest
class AuthFlowTest {

    private static final String SESSION_COOKIE = "access_token";

    @Inject
    UserRepository userRepository;

    @Inject
    CustomerRepository customerRepository;

    @Inject
    PasswordHasher passwordHasher;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.deleteAll();
            customerRepository.deleteAll();
        });
    }

    @Test
    void registerCreatesUserRoleEvenIfAdminIsRequested() {
        Csrf.given().contentType(ContentType.JSON)
                .body(credentials("ana@ejemplo.com", "secreto123").replace("}", ", \"role\": \"admin\"}"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("email", is("ana@ejemplo.com"))
                .body("role", is("user"))
                .body("$", not(hasKey("password")));
    }

    @Test
    void loginSetsHttpOnlyStrictCookieAndUserCanOnlyRead() {
        register("ana@ejemplo.com", "secreto123");

        var response = Csrf.given().contentType(ContentType.JSON)
                .body(credentials("ana@ejemplo.com", "secreto123"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("email", is("ana@ejemplo.com"))
                .body("role", is("user"))
                .body("$", not(hasKey("password")))
                .extract();

        Cookie cookie = response.detailedCookie(SESSION_COOKIE);
        assertThat(cookie.getValue(), not(blankOrNullString()));
        assertThat(cookie.isHttpOnly(), is(true));
        assertThat(cookie.getSameSite(), equalToIgnoringCase("Strict"));
        assertThat(cookie.getMaxAge(), greaterThan(0L));
        String token = cookie.getValue();

        RestAssured.given().cookie(SESSION_COOKIE, token)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("email", is("ana@ejemplo.com"));
        RestAssured.given().cookie(SESSION_COOKIE, token)
                .when().get("/api/customers")
                .then().statusCode(200);
        Csrf.given().cookie(SESSION_COOKIE, token).contentType(ContentType.JSON)
                .body(customer("luis@ejemplo.com"))
                .when().post("/api/customers")
                .then().statusCode(403);
    }

    @Test
    void adminCanCreateCustomers() {
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = new User();
            admin.setEmail("admin@ejemplo.com");
            admin.setPassword(passwordHasher.hash("secreto123"));
            admin.setRole(Role.ADMIN);
            userRepository.persist(admin);
        });
        String token = login("admin@ejemplo.com", "secreto123");

        Csrf.given().cookie(SESSION_COOKIE, token).contentType(ContentType.JSON)
                .body(customer("luis@ejemplo.com"))
                .when().post("/api/customers")
                .then().statusCode(201);
    }

    @Test
    void wrongCredentialsReturnTheSameGeneric401() {
        register("ana@ejemplo.com", "secreto123");

        for (String body : new String[]{credentials("ana@ejemplo.com", "incorrecto"),
                credentials("nadie@ejemplo.com", "secreto123")}) {
            Csrf.given().contentType(ContentType.JSON)
                    .body(body)
                    .when().post("/api/auth/login")
                    .then().statusCode(401)
                    .body("error", is("No autenticado"))
                    .body("message", is("Email o contraseña incorrectos."));
        }
    }

    @Test
    void meWithoutSessionReturns204AndWithInvalidToken401() {
        RestAssured.given().when().get("/api/auth/me")
                .then().statusCode(204);
        RestAssured.given().cookie(SESSION_COOKIE, "token-invalido")
                .when().get("/api/auth/me")
                .then().statusCode(401);
    }

    @Test
    void withoutSessionReturns401Json() {
        RestAssured.given().when().get("/api/customers")
                .then().statusCode(401)
                .contentType(ContentType.JSON)
                .body("message", is("Debes iniciar sesión para continuar."));
        RestAssured.given().cookie(SESSION_COOKIE, "token-invalido")
                .when().get("/api/customers")
                .then().statusCode(401);
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() {
        register("ana@ejemplo.com", "secreto123");

        RestAssured.given().contentType(ContentType.JSON)
                .body(credentials("ana@ejemplo.com", "secreto123"))
                .when().post("/api/auth/login")
                .then().statusCode(400);
        // Cabecera que no coincide con la cookie
        RestAssured.given().cookie(Csrf.COOKIE, Csrf.token()).header(Csrf.HEADER, "otro-valor")
                .contentType(ContentType.JSON)
                .body(credentials("ana@ejemplo.com", "secreto123"))
                .when().post("/api/auth/login")
                .then().statusCode(400);
    }

    @Test
    void logoutExpiresTheCookie() {
        register("ana@ejemplo.com", "secreto123");
        String token = login("ana@ejemplo.com", "secreto123");

        Cookie cookie = Csrf.given().cookie(SESSION_COOKIE, token)
                .when().post("/api/auth/logout")
                .then().statusCode(204)
                .extract().detailedCookie(SESSION_COOKIE);
        assertThat(cookie.getMaxAge(), is(0L));
    }

    private void register(String email, String password) {
        Csrf.given().contentType(ContentType.JSON)
                .body(credentials(email, password))
                .when().post("/api/auth/register")
                .then().statusCode(201);
    }

    private String login(String email, String password) {
        return Csrf.given().contentType(ContentType.JSON)
                .body(credentials(email, password))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().cookie(SESSION_COOKIE);
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    private static String customer(String email) {
        return """
                {"name": "Luis", "lastName": "Soto", "email": "%s"}""".formatted(email);
    }
}
