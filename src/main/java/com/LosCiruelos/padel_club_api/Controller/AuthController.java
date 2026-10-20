package com.LosCiruelos.padel_club_api.Controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Requests.EmailRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.GoogleLoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.LoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.ReenviarCodigoRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RefreshTokenRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RegisterRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.ResetPasswordRequest;
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
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verificar-token")
    public ResponseEntity<Void> verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        if (request.getTipo_codigo() == TokenType.VERIFY_EMAIL) {
            verificationService.verificarToken(request.getEmail(), request.getCodigo());
        } else {
            passwordResetService.verificarToken(request.getEmail(), request.getCodigo());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-token")
    public ResponseEntity<Void> reenviarToken(@Valid @RequestBody ReenviarCodigoRequest request) {
        if (request.getTipo_codigo() == TokenType.VERIFY_EMAIL) {
            verificationService.enviarToken(request.getEmail());
        } else {
            passwordResetService.enviarToken(request.getEmail());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> enviarTokenResetPassword(@Valid @RequestBody EmailRequest request) {
        passwordResetService.enviarToken(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/password-reset/confirm")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getEmail(), request.getNuevaPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request.getIdToken()));
    }
}
