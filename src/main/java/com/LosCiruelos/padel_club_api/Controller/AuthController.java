package com.LosCiruelos.padel_club_api.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Requests.GoogleLoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.LoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RegisterRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.VerificarEmailRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.LoginResponse;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;
import com.LosCiruelos.padel_club_api.Services.AuthService;
import com.LosCiruelos.padel_club_api.Services.PasswordResetService;
import com.LosCiruelos.padel_club_api.Services.VerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verificar")
    public ResponseEntity<Void> verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        if (request.getTipo_codigo() == TokenType.VERIFY_EMAIL) {
            verificationService.verificarEmail(request.getEmail(), request.getCodigo());
        } else {
            passwordResetService.verificarEmail(request.getEmail(), request.getCodigo());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String tipo_codigo = body.get("tipo_codigo");
        if (TokenType.valueOf(tipo_codigo) == TokenType.VERIFY_EMAIL) {
            verificationService.reenviarTokenVerificacion(email);
        } else {
            passwordResetService.reenviarVerificacion(email);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request.getIdToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(Map.of("accessToken", authService.refreshToken(request.get("refreshToken"))));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token requerido"));
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> body) {
        passwordResetService.enviarResetPassword(body.get("email"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@RequestBody Map<String, String> body) {
        passwordResetService.resetPassword(
                body.get("email"),
                body.get("nuevaPassword"));
        return ResponseEntity.ok().build();
    }
}
