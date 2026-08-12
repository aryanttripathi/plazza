package org.plazza.plazza.user.api;

import jakarta.validation.Valid;
import org.plazza.plazza.user.RegisterUserCommand;
import org.plazza.plazza.user.UserService;
import org.plazza.plazza.user.api.dto.RegisterUserRequest;
import org.plazza.plazza.user.api.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse body = UserResponse.from(
                userService.register(new RegisterUserCommand(request.name(), request.phone())));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return UserResponse.from(userService.requireById(id));
    }
}
