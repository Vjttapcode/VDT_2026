package com.vdt.auth_service.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vdt.auth_service.dto.CompanyDto;
import com.vdt.auth_service.service.CompanyService;

@RestController
@RequestMapping("/companies")
public class CompanyController {
    private final CompanyService service;

    public CompanyController(CompanyService service){
        this.service = service;
    }

    @GetMapping
    public List<CompanyDto> list() { return service.findAll();}

    @PostMapping
    public ResponseEntity<CompanyDto> create(@Valid @RequestBody CompanyDto dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public CompanyDto update(@PathVariable Long id, @Valid @RequestBody CompanyDto dto){
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){ service.delete(id); }
}