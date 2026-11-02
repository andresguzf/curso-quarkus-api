package com.andres.course.claude.quarkus.quarkusapi.app.services;

import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerDto;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.CustomerFilter;
import com.andres.course.claude.quarkus.quarkusapi.app.models.dto.PageDto;

import java.util.List;

public interface CustomerService {

    // Página de clientes según los filtros y el orden pedidos
    PageDto<CustomerDto> search(CustomerFilter filter);

    CustomerDto findById(Long id);

    CustomerDto findByEmail(String email);

    List<CustomerDto> searchByName(String term);

    CustomerDto create(CustomerDto dto);

    CustomerDto update(Long id, CustomerDto dto);

    void delete(Long id);
}
