package study.min.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import study.min.gateway.entity.User;
import study.min.gateway.jwt.JwtUtil;
import study.min.gateway.repository.UserRepository;
import study.min.gateway.service.dto.LoginCommand;
import study.min.gateway.service.dto.LoginResult;
import study.min.gateway.service.dto.RegisterCommand;
import study.min.gateway.service.dto.RegisterResult;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public Mono<RegisterResult> register(RegisterCommand command) {
        return userRepository.findByUsername(command.username())
                .flatMap(existing -> Mono.<RegisterResult>error(
                        new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    User user = new User(command.username(), passwordEncoder.encode(command.password()), "USER");
                    return userRepository.save(user)
                            .map(saved -> new RegisterResult("User registered", saved.getId()));
                }));
    }

    public Mono<LoginResult> login(LoginCommand command) {
        return userRepository.findByUsername(command.username())
                .filter(user -> passwordEncoder.matches(command.password(), user.getPassword()))
                .map(user -> {
                    String token = jwtUtil.generateToken(user.getUsername(), Map.of(
                            "role", user.getRole(),
                            "userId", user.getId()
                    ));
                    return new LoginResult(token);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")));
    }
}
