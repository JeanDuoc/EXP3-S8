package cl.duoc.cuentasservice.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private ResponseEntity<?> error(int status, String code, String msg) {
    return ResponseEntity.status(status)
        .body(Map.of("codigo", code, "mensaje", msg, "fecha", Instant.now().toString()));
  }

  @ExceptionHandler(NoEncontradoException.class)
  ResponseEntity<?> missing(NoEncontradoException e) {
    return error(404, "NO_ENCONTRADO", e.getMessage());
  }

  @ExceptionHandler(ServicioNoDisponibleException.class)
  ResponseEntity<?> unavailable(ServicioNoDisponibleException e) {
    return error(503, "SERVICIO_NO_DISPONIBLE", e.getMessage());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<?> auth(AuthenticationException e) {
    return error(401, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos");
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> bad(Exception e) {
    return error(400, "DATOS_INVALIDOS", "Revisa los parámetros o el cuerpo de la solicitud");
  }
}
