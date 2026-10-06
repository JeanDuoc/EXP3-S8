package cl.duoc.cuentasservice.controller;

import cl.duoc.cuentasservice.eventos.ReporteService;
import cl.duoc.cuentasservice.model.ReporteSolicitud;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes/solicitudes")
public class ReporteController {
  private final ReporteService reportes;

  public ReporteController(ReporteService reportes) {
    this.reportes = reportes;
  }

  @PostMapping
  public ResponseEntity<ReporteSolicitud> solicitar() {
    var solicitud = reportes.solicitar();
    return ResponseEntity.accepted()
        .location(URI.create("/api/reportes/solicitudes/" + solicitud.id()))
        .body(solicitud);
  }

  @GetMapping("/{id}")
  public ReporteSolicitud buscar(@PathVariable UUID id) {
    return reportes.buscar(id.toString());
  }
}
