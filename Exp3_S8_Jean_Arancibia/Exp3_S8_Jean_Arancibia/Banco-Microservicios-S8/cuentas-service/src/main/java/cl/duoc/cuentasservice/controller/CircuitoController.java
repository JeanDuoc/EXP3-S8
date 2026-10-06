package cl.duoc.cuentasservice.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/circuito")
public class CircuitoController {
  private final CircuitBreakerRegistry registry;

  public CircuitoController(CircuitBreakerRegistry registry) {
    this.registry = registry;
  }

  @GetMapping
  public Map<String, Object> estado() {
    var b = registry.circuitBreaker("banco");
    return Map.of(
        "nombre",
        b.getName(),
        "estado",
        b.getState().name(),
        "llamadasFallidas",
        b.getMetrics().getNumberOfFailedCalls(),
        "llamadasExitosas",
        b.getMetrics().getNumberOfSuccessfulCalls(),
        "llamadasRechazadas",
        b.getMetrics().getNumberOfNotPermittedCalls());
  }
}
