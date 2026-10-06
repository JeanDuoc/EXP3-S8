package cl.duoc.cuentasservice.controller;

import cl.duoc.cuentasservice.model.*;
import cl.duoc.cuentasservice.service.BancoService;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BancoController {
  private final BancoService service;
  private final String configSource;

  public BancoController(BancoService service, @Value("${app.config-source}") String configSource) {
    this.service = service;
    this.configSource = configSource;
  }

  @GetMapping("/cuentas")
  public List<Cuenta> cuentas() {
    return service.cuentas();
  }

  @GetMapping("/cuentas/{id}")
  public Cuenta cuenta(@PathVariable @Min(1) long id) {
    return service.cuenta(id);
  }

  @GetMapping("/transacciones")
  public List<Transaccion> transacciones(
      @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return service.transacciones(limite, offset);
  }

  @GetMapping("/transacciones/{id}")
  public Transaccion transaccion(@PathVariable @Min(1) long id) {
    return service.transaccion(id);
  }

  @GetMapping("/reportes/anuales")
  public List<MovimientoAnual> anuales(
      @RequestParam(required = false) @Min(1) Long cuentaId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return service.anuales(cuentaId, limite, offset);
  }

  @GetMapping("/info")
  public Map<String, String> info() {
    return Map.of("aplicacion", "cuentas-service", "configuracion", configSource);
  }
}
