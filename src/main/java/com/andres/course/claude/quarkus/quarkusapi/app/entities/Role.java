package com.andres.course.claude.quarkus.quarkusapi.app.entities;

// Rol del usuario; group es el nombre que viaja en el claim groups del JWT y usa @RolesAllowed
public enum Role {

    USER(Names.USER),
    ADMIN(Names.ADMIN);

    private final String group;

    Role(String group) {
        this.group = group;
    }

    public String group() {
        return group;
    }

    // "admin" -> ADMIN; null si el valor no corresponde a ningún rol
    public static Role fromGroup(String group) {
        for (Role role : values()) {
            if (role.group.equals(group)) {
                return role;
            }
        }
        return null;
    }

    // Constantes para @RolesAllowed, que solo acepta valores constantes
    public static final class Names {

        public static final String USER = "user";
        public static final String ADMIN = "admin";

        private Names() {
        }
    }
}
