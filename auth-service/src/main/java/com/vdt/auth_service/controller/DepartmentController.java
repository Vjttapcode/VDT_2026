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

import com.vdt.auth_service.dto.DepartmentDto;
import com.vdt.auth_service.service.DepartmentService;

@RestController
@RequestMapping("/departments")
public class DepartmentController {
    private final DepartmentService service;

    public DepartmentController(DepartmentService service) { this.service = service; }

    @GetMapping
    public List<DepartmentDto> list() { return service.findAll(); }

    @PostMapping
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody DepartmentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public DepartmentDto update(@PathVariable Long id, @Valid @RequestBody DepartmentDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}