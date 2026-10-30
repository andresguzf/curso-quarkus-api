package com.andres.course.claude.quarkus.quarkusapi.app.mappers;

import com.andres.course.claude.quarkus.quarkusapi.app.entities.Customer;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerMapper {

    // entity -> dto
    public CustomerDto toDto(Customer entity) {
        if (entity == null) {
            return null;
        }
        return new CustomerDto(entity.getId(), entity.getName(), entity.getLastName(), entity.getEmail());
    }

    // dto -> entity
    public Customer toEntity(CustomerDto dto) {
        if (dto == null) {
            return null;
        }
        Customer entity = new Customer();
        updateEntity(entity, dto);
        return entity;
    }

    // Solo campos editables: nunca id ni fechas de auditoría
    public void updateEntity(Customer entity, CustomerDto dto) {
        entity.setName(dto.name());
        entity.setLastName(dto.lastName());
        entity.setEmail(dto.email());
    }
}
