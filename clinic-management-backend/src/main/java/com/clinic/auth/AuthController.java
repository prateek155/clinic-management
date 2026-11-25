package com.clinic.auth;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public UserSession register(@AuthenticationPrincipal FirebasePrincipal principal, @Valid @RequestBody RegisterRequest request) {
        return authService.register(principal, request);
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public UserSession currentUser(@AuthenticationPrincipal FirebasePrincipal principal) {
        return authService.currentUser(principal);
    }
}
