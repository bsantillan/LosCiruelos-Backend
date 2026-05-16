package com.LosCiruelos.padel_club_api.Handlers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaDesactivadaException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaVerificadaException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailNoVerificadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Exceptions.TerminosNoAceptadosException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

        // Handlers de Registro

        @ExceptionHandler(EmailEnUsoException.class)
        public ResponseEntity<Map<String, Object>> handleEmailEnUso(EmailEnUsoException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("errors", Map.of(
                                "email", ex.getMessage()));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(CuentaDesactivadaException.class)
        public ResponseEntity<Map<String, Object>> handleCuentaDesactivada(CuentaDesactivadaException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("error", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(body);
        }

        @ExceptionHandler(PasswordInvalidaException.class)
        public ResponseEntity<Map<String, Object>> handlePasswordInvalida(PasswordInvalidaException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("errors", Map.of(
                                "password", ex.getMessage()));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(TerminosNoAceptadosException.class)
        public ResponseEntity<Map<String, Object>> handleTerminosNoAceptados(TerminosNoAceptadosException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("errors", Map.of(
                                "terminos", ex.getMessage()));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        // Handlers de Login

        @ExceptionHandler(CredencialesInvalidasException.class)
        public ResponseEntity<Map<String, Object>> handleCredencialesInvalidas(CredencialesInvalidasException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("error", ex.getMessage());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        @ExceptionHandler(CodigoInvalidoException.class)
        public ResponseEntity<Map<String, Object>> handleCodigoInvalido(CodigoInvalidoException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("errors", Map.of(
                                "codigo", ex.getMessage()));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(CuentaVerificadaException.class)
        public ResponseEntity<Map<String, Object>> handleCuentaVerificada(CuentaVerificadaException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("errors", Map.of(
                                "cuenta_verificada", ex.getMessage()));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        // Handlers de Registro/Login
        @ExceptionHandler(EmailNoVerificadoException.class)
        public ResponseEntity<Map<String, Object>> handleEmailNoVerificado(
                        EmailNoVerificadoException ex,
                        HttpServletRequest request) {
                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());

                if (request.getRequestURI().contains("/auth/register")) {
                        body.put("errors", Map.of(
                                        "email", ex.getMessage()));
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
                }

                body.put("errors", Map.of(
                                "email_verificado", ex.getMessage()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleOtrasExceptions(Exception ex) {

                ex.printStackTrace();

                Map<String, Object> body = new HashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("error", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(body);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
                Map<String, String> errors = new HashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
                Map<String, Object> body = new HashMap<>();
                body.put("status", 400);
                body.put("errors", errors);
                return ResponseEntity.badRequest().body(body);
        }
}
