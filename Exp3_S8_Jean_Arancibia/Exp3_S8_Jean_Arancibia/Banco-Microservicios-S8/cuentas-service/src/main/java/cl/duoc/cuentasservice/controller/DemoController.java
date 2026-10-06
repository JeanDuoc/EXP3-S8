package cl.duoc.cuentasservice.controller;

import cl.duoc.cuentasservice.config.FalloSimulado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("demo")
@RequestMapping("/api/demo/fallo")
public class DemoController {
  private final FalloSimulado fallo;

  public DemoController(FalloSimulado fallo) {
    this.fallo = fallo;
  }

  public record Solicitud(@NotNull Boolean activo) {}

  @PostMapping
  public Map<String, Object> activar(@Valid @RequestBody Solicitud body) {
    fallo.activar(body.activo());
    return Map.of(
        "falloSimulado",
        fallo.activo(),
        "mensaje",
        fallo.activo()
            ? "Fallo activado para pruebas"
            : "Fallo desactivado; el circuito se recuperará con las siguientes consultas");
  }
}
