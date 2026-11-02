package com.andres.course.claude.quarkus.quarkusapi.app.security;

import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

/**
 * Firma el JWT de la sesión. El issuer y la clave privada salen de la configuración de smallrye-jwt
 * (en dev y test Quarkus genera el par de claves en memoria).
 */
@ApplicationScoped
public class TokenService {

    private final Duration duration;

    public TokenService(@ConfigProperty(name = "app.auth.token-duration") Duration duration) {
        this.duration = duration;
    }

    public String generate(UserDto user) {
        return Jwt.upn(user.email())
                .subject(String.valueOf(user.id()))
                .groups(Set.of(user.role()))
                .expiresIn(duration)
                .sign();
    }

    public Duration duration() {
        return duration;
    }
}
