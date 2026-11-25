package com.clinic.security;

import com.clinic.user.ClinicUser;
import com.clinic.user.ClinicUserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final FirebaseAuth firebaseAuth;
    private final ClinicUserRepository users;

    public FirebaseAuthenticationFilter(
            org.springframework.beans.factory.ObjectProvider<FirebaseAuth> firebaseAuth,
            ClinicUserRepository users) {
        this.firebaseAuth = firebaseAuth.getIfAvailable();
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        if (firebaseAuth == null) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication service is not configured");
            return;
        }

        FirebaseToken token;
        try {
            String idToken = authorization.substring(7).trim();
            if (idToken.isEmpty()) throw new IllegalArgumentException("Missing ID token");
            token = firebaseAuth.verifyIdToken(idToken, true);
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked Firebase sign-in");
            return;
        }

        boolean registrationRequest = request.getMethod().equals("POST") && request.getRequestURI().equals(REGISTER_PATH);
        if (!registrationRequest) {
            ClinicUser account = users.findByFirebaseUid(token.getUid()).orElse(null);
            if (account == null || account.isDisabled()) {
                SecurityContextHolder.clearContext();
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "No active clinic account is linked to this sign-in");
                return;
            }
        }

        boolean verified = Boolean.TRUE.equals(token.getClaims().get("email_verified"));
        FirebasePrincipal principal = new FirebasePrincipal(token.getUid(), token.getEmail(), token.getName(), verified);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
