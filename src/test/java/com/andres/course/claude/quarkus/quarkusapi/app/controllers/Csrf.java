package com.andres.course.claude.quarkus.quarkusapi.app.controllers;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

// Pide un token CSRF y lo envía como cookie y en la cabecera X-CSRF-TOKEN, como lo haría el frontend
final class Csrf {

    static final String COOKIE = "csrf-token";
    static final String HEADER = "X-CSRF-TOKEN";

    private Csrf() {
    }

    static String token() {
        return RestAssured.given().when().get("/api/auth/csrf")
                .then().statusCode(204)
                .extract().cookie(COOKIE);
    }

    static RequestSpecification given() {
        String token = token();
        return RestAssured.given().cookie(COOKIE, token).header(HEADER, token);
    }
}
