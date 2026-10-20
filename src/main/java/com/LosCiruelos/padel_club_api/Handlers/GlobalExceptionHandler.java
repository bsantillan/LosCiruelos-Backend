package com.LosCiruelos.padel_club_api.Handlers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.LosCiruelos.padel_club_api.Config.Fechas;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.CategoriaException;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaDesactivadaException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaVerificadaException;
import com.LosCiruelos.padel_club_api.Exceptions.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailNoVerificadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.TerminosNoAceptadosException;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduce excepciones a respuestas HTTP. Dos formas de body:
 * {"timestamp", "error": "mensaje"} o {"timestamp", "errors": {"campo": "mensaje"}}.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

        private final Clock clock;

        // ── Registro / login ─────────────────────────────────────────────

        @ExceptionHandler(EmailEnUsoException.class)
        public ResponseEntity<Map<String, Object>> handleEmailEnUso(EmailEnUsoException ex) {
                return errores(HttpStatus.BAD_REQUEST, Map.of("email", ex.getMessage()));
        }

        @ExceptionHandler(PasswordInvalidaException.class)
        public ResponseEntity<Map<String, Object>> handlePasswordInvalida(PasswordInvalidaException ex) {
                return errores(HttpStatus.BAD_REQUEST, Map.of("password", ex.getMessage()));
        }

        @ExceptionHandler(TerminosNoAceptadosException.class)
        public ResponseEntity<Map<String, Object>> handleTerminosNoAceptados(TerminosNoAceptadosException ex) {
                return errores(HttpStatus.BAD_REQUEST, Map.of("terminos", ex.getMessage()));
        }

        @ExceptionHandler(CodigoInvalidoException.class)
        public ResponseEntity<Map<String, Object>> handleCodigoInvalido(CodigoInvalidoException ex) {
                return errores(HttpStatus.BAD_REQUEST, Map.of("codigo", ex.getMessage()));
        }

        @ExceptionHandler(CuentaVerificadaException.class)
        public ResponseEntity<Map<String, Object>> handleCuentaVerificada(CuentaVerificadaException ex) {
                return errores(HttpStatus.CONFLICT, Map.of("cuenta_verificada", ex.getMessage()));
        }

        @ExceptionHandler(EmailNoVerificadoException.class)
        public ResponseEntity<Map<String, Object>> handleEmailNoVerificado(EmailNoVerificadoException ex,
                        HttpServletRequest request) {
                if (request.getRequestURI().contains("/auth/register")) {
                        return errores(HttpStatus.CONFLICT, Map.of("email", ex.getMessage()));
                }
                return errores(HttpStatus.FORBIDDEN, Map.of("email_verificado", ex.getMessage()));
        }

        @ExceptionHandler(CredencialesInvalidasException.class)
        public ResponseEntity<Map<String, Object>> handleCredencialesInvalidas(CredencialesInvalidasException ex) {
                return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }

        @ExceptionHandler(CuentaDesactivadaException.class)
        public ResponseEntity<Map<String, Object>> handleCuentaDesactivada(CuentaDesactivadaException ex) {
                return error(HttpStatus.FORBIDDEN, ex.getMessage());
        }

        // ── Perfil / usuarios ────────────────────────────────────────────

        @ExceptionHandler(UsuarioNotFoundException.class)
        public ResponseEntity<Map<String, Object>> handleUsuarioNotFound(UsuarioNotFoundException ex) {
                return error(HttpStatus.NOT_FOUND, ex.getMessage());
        }

        @ExceptionHandler(PerfilIncompletoException.class)
        public ResponseEntity<Map<String, Object>> handlePerfilIncompleto(PerfilIncompletoException ex) {
                return error(HttpStatus.FORBIDDEN, ex.getMessage());
        }

        @ExceptionHandler(CategoriaException.class)
        public ResponseEntity<Map<String, Object>> handleCategoria(CategoriaException ex) {
                return error(HttpStatus.FORBIDDEN, ex.getMessage());
        }

        @ExceptionHandler(ValidationException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
                return errores(HttpStatus.BAD_REQUEST, ex.getErrores());
        }

        // ── Canchas, reservas y pagos ────────────────────────────────────

        @ExceptionHandler(CanchaNotFoundException.class)
        public ResponseEntity<Map<String, Object>> handleCancha(CanchaNotFoundException ex) {
                return error(HttpStatus.NOT_FOUND, ex.getMessage());
        }

        @ExceptionHandler(DiaSinAperturaException.class)
        public ResponseEntity<Map<String, Object>> handleDiaApertura(DiaSinAperturaException ex) {
                return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        /** 400 datos inválidos · 404 no encontrada · 409 estado o turno en conflicto · 502 falla de MP. */
        @ExceptionHandler(ReservaException.class)
        public ResponseEntity<Map<String, Object>> handleReserva(ReservaException ex) {
                return error(ex.getStatus(), ex.getMessage());
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
                log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
                return error(HttpStatus.CONFLICT, "La operación entra en conflicto con datos existentes.");
        }

        @ExceptionHandler(PessimisticLockingFailureException.class)
        public ResponseEntity<Map<String, Object>> handleLock(PessimisticLockingFailureException ex) {
                return error(HttpStatus.CONFLICT, "La reserva se está modificando en este momento. Intentá de nuevo.");
        }

        // ── Seguridad ────────────────────────────────────────────────────

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
                return error(HttpStatus.FORBIDDEN, "No tenés permisos para esta operación");
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
                return error(HttpStatus.UNAUTHORIZED, "No autenticado o token vencido");
        }

        // ── Pedidos mal formados ─────────────────────────────────────────

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
                Map<String, String> errors = new LinkedHashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
                return errores(HttpStatus.BAD_REQUEST, errors);
        }

        @ExceptionHandler(HandlerMethodValidationException.class)
        public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
                return error(HttpStatus.BAD_REQUEST, "Parámetros inválidos.");
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
                return errores(HttpStatus.BAD_REQUEST, Map.of(ex.getParameterName(), "Es obligatorio"));
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
                return error(HttpStatus.BAD_REQUEST, "Falta el header " + ex.getHeaderName());
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
                return errores(HttpStatus.BAD_REQUEST, Map.of(ex.getName(), "Valor inválido"));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
                return error(HttpStatus.BAD_REQUEST, "El cuerpo del pedido es inválido o tiene un valor no permitido.");
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
                return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
                return error(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido");
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
                return error(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }

        // ── Cualquier otra: 500 sin exponer detalles internos ────────────

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleOtrasExceptions(Exception ex) {
                log.error("Error no controlado", ex);
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intentá de nuevo.");
        }

        // ── helpers ──────────────────────────────────────────────────────

        /** Mismo formato que el resto de las fechas de la API: 2026-10-17T18:15:00.123-03:00. */
        private String timestamp() {
                return Fechas.formatear(LocalDateTime.now(clock));
        }

        private ResponseEntity<Map<String, Object>> error(HttpStatus status, String mensaje) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("timestamp", timestamp());
                body.put("error", mensaje);
                return ResponseEntity.status(status).body(body);
        }

        private ResponseEntity<Map<String, Object>> errores(HttpStatus status, Map<String, ?> errores) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("timestamp", timestamp());
                body.put("errors", errores);
                return ResponseEntity.status(status).body(body);
        }
}
