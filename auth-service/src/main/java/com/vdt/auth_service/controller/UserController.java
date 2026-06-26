package com.vdt.auth_service.controller;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public userController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.findAll();
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @RequestBody UserDto dto){
        return userService.update(id, dto);
    }
}