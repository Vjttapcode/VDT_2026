package com.vdt.auth_service.controller;

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

}