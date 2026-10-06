package cl.duoc.cuentasservice.eventos;

import cl.duoc.cuentasservice.exception.NoEncontradoException;
import cl.duoc.cuentasservice.model.ReporteSolicitud;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReporteService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ReporteService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional
  public ReporteSolicitud solicitar() {
    var id = UUID.randomUUID().toString();
    var now = Instant.now();
    jdbc.update(
        "INSERT INTO solicitudes_reporte(id,estado,creado_en) VALUES(?,?,?)",
        id,
        "PENDIENTE",
        Timestamp.from(now));
    // La solicitud y el evento quedan en la misma transacción de base de datos.
    jdbc.update("INSERT INTO eventos_outbox(id,pendiente,intentos) VALUES(?,?,?)", id, true, 0);
    return new ReporteSolicitud(id, "PENDIENTE", now, null, null);
  }

  public ReporteSolicitud buscar(String id) {
    return jdbc
        .query(
            "SELECT * FROM solicitudes_reporte WHERE id=?",
            (r, n) -> {
              String raw = r.getString("resultado");
              var completed = r.getTimestamp("completado_en");
              try {
                return new ReporteSolicitud(
                    r.getString("id"),
                    r.getString("estado"),
                    r.getTimestamp("creado_en").toInstant(),
                    completed == null ? null : completed.toInstant(),
                    raw == null ? null : mapper.readTree(raw));
              } catch (java.io.IOException e) {
                throw new IllegalStateException("Resultado de reporte inválido", e);
              }
            },
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new NoEncontradoException("Solicitud de reporte no encontrada: " + id));
  }

  @Transactional
  public void completar(String id) throws Exception {
    var estados =
        jdbc.queryForList(
            "SELECT estado FROM solicitudes_reporte WHERE id=? FOR UPDATE", String.class, id);
    if (estados.isEmpty()) throw new IllegalArgumentException("Mensaje sin solicitud registrada");
    // Entrega al menos una vez: un duplicado no vuelve a ejecutar un reporte completado.
    if (estados.get(0).equals("COMPLETADO")) return;
    Map<String, Object> reporte = new LinkedHashMap<>();
    reporte.put("cuentas", jdbc.queryForObject("SELECT COUNT(*) FROM cuentas", Long.class));
    reporte.put(
        "saldoTotal",
        jdbc.queryForObject("SELECT SUM(saldo) FROM cuentas", java.math.BigDecimal.class));
    reporte.put(
        "interesTotal",
        jdbc.queryForObject(
            "SELECT SUM(interes_calculado) FROM cuentas", java.math.BigDecimal.class));
    reporte.put(
        "transacciones", jdbc.queryForObject("SELECT COUNT(*) FROM transacciones", Long.class));
    reporte.put(
        "movimientosAnuales",
        jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_anuales", Long.class));
    jdbc.update(
        "UPDATE solicitudes_reporte SET estado=?,completado_en=?,resultado=? WHERE id=?",
        "COMPLETADO",
        Timestamp.from(Instant.now()),
        mapper.writeValueAsString(reporte),
        id);
    jdbc.update("UPDATE eventos_outbox SET pendiente=? WHERE id=?", false, id);
  }

  public List<String> pendientes() {
    return jdbc.queryForList(
        "SELECT id FROM eventos_outbox WHERE pendiente=? ORDER BY id LIMIT 25", String.class, true);
  }

  public void registrarIntento(String id) {
    jdbc.update(
        "UPDATE eventos_outbox SET intentos=intentos+1,ultimo_envio=? WHERE id=?",
        Timestamp.from(Instant.now()),
        id);
  }
}
