package com.andres.course.claude.quarkus.quarkusapi.app.mappers;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Role;
import com.andres.course.claude.quarkus.quarkusapi.app.entities.User;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.UserDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserMapper {

    // entity -> dto: nunca copia el password
    public UserDto toDto(User entity) {
        if (entity == null) {
            return null;
        }
        return new UserDto(entity.getId(), entity.getEmail(), null, entity.getRole().group());
    }

    // dto -> entity: el password lo hashea el service, no el mapper
    public User toEntity(UserDto dto) {
        if (dto == null) {
            return null;
        }
        User entity = new User();
        // Un usuario nuevo sin rol es user
        entity.setRole(Role.USER);
        updateEntity(entity, dto);
        return entity;
    }

    // Solo email y rol: nunca id, password ni fechas de auditoría. Sin rol se conserva el actual
    public void updateEntity(User entity, UserDto dto) {
        entity.setEmail(dto.email());
        Role role = Role.fromGroup(dto.role());
        if (role != null) {
            entity.setRole(role);
        }
    }
}
