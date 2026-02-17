package study.min.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import study.min.gateway.controller.dto.LoginRequest;
import study.min.gateway.controller.dto.LoginResponse;
import study.min.gateway.controller.dto.RegisterRequest;
import study.min.gateway.controller.dto.RegisterResponse;
import study.min.gateway.service.AuthService;
import study.min.gateway.service.dto.LoginCommand;
import study.min.gateway.service.dto.RegisterCommand;

@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Mono<RegisterResponse> register(@RequestBody RegisterRequest request) {
        if (request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password required");
        }

        return authService.register(new RegisterCommand(request.username(), request.password()))
                .map(result -> new RegisterResponse(result.message(), result.userId()));
    }

    @PostMapping("/token")
    public Mono<LoginResponse> issueToken(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password required");
        }

        return authService.login(new LoginCommand(request.username(), request.password()))
                .map(result -> new LoginResponse(result.token()));
    }
}
