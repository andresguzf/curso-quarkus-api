package com.andres.course.claude.quarkus.quarkusapi.app.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;

import java.security.GeneralSecurityException;

/**
 * Hash y verificación de passwords con bcrypt (Elytron, la librería de seguridad de Quarkus).
 */
@ApplicationScoped
public class PasswordHasher {

    // Se verifica contra este hash cuando el email no existe, para que el login tarde lo mismo exista o no
    private final String dummyHash = BcryptUtil.bcryptHash("password-ficticio-para-igualar-tiempos");

    public String hash(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    public boolean matches(String password, String hash) {
        try {
            PasswordFactory factory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT,
                    WildFlyElytronPasswordProvider.getInstance());
            Password stored = factory.translate(ModularCrypt.decode(hash));
            return factory.verify(stored, password.toCharArray());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // Gasta el mismo tiempo que una verificación real; el resultado se descarta
    public void simulateMatch(String password) {
        matches(password, dummyHash);
    }
}
