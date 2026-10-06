package cl.duoc.cuentasservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ReporteSolicitud(
    String id, String estado, Instant creadoEn, Instant completadoEn, JsonNode resultado) {}
