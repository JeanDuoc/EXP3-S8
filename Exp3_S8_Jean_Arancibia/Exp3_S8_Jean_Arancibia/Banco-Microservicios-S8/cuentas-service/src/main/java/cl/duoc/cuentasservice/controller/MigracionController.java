package cl.duoc.cuentasservice.controller;

import cl.duoc.cuentasservice.service.BancoService;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/migracion")
public class MigracionController {
  private final BancoService service;
  private final JsonNode resumen;
  private final byte[] rechazos;

  public MigracionController(BancoService service, ObjectMapper mapper) throws IOException {
    this.service = service;
    try (var stream = new ClassPathResource("migrados/resumen_migracion.json").getInputStream()) {
      resumen = mapper.readTree(stream);
    }
    try (var stream = new ClassPathResource("migrados/rechazos.csv").getInputStream()) {
      rechazos = stream.readAllBytes();
    }
  }

  @GetMapping
  public Map<String, Object> resumen() {
    return Map.of("migracion", resumen, "baseDeDatos", service.conteos());
  }

  @GetMapping(value = "/rechazos", produces = "text/csv")
  public ResponseEntity<byte[]> rechazos() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rechazos.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(rechazos);
  }
}
