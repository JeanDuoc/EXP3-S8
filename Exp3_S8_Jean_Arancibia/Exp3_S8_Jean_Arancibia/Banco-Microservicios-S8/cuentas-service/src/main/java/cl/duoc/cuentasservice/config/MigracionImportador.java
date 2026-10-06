package cl.duoc.cuentasservice.config;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import org.apache.commons.csv.*;
import org.slf4j.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MigracionImportador {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private static final Logger log = LoggerFactory.getLogger(MigracionImportador.class);

  public MigracionImportador(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional
  public void importar() throws IOException {
    JsonNode resumen;
    try (var stream = new ClassPathResource("migrados/resumen_migracion.json").getInputStream()) {
      resumen = mapper.readTree(stream);
    }
    String lote = resumen.get("lote_sha256").asText();
    Long lotes = jdbc.queryForObject("SELECT COUNT(*) FROM migracion_lote", Long.class);
    if (lotes > 0) {
      Long existente =
          jdbc.queryForObject("SELECT COUNT(*) FROM migracion_lote WHERE id=?", Long.class, lote);
      if (existente == 0)
        throw new IllegalStateException(
            "La base contiene otro lote. Usa una base vacía para importar nuevos datos.");
      log.info("MIGRACION lote {} ya importado; no se duplican registros", lote);
      return;
    }
    if (jdbc.queryForObject("SELECT COUNT(*) FROM cuentas", Long.class) > 0
        || jdbc.queryForObject("SELECT COUNT(*) FROM transacciones", Long.class) > 0
        || jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_anuales", Long.class) > 0)
      throw new IllegalStateException(
          "La importación necesita una base vacía o un lote ya registrado");
    try (var csv = leer("cuentas_migradas.csv")) {
      for (var r : csv)
        jdbc.update(
            "INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo,interes_calculado,saldo_final)"
                + " VALUES(?,?,?,?,?,?,?)",
            Long.parseLong(r.get("cuenta_id")),
            r.get("nombre"),
            new BigDecimal(r.get("saldo")),
            Integer.parseInt(r.get("edad")),
            r.get("tipo"),
            new BigDecimal(r.get("interes_calculado")),
            new BigDecimal(r.get("saldo_final")));
    }
    try (var csv = leer("transacciones_migradas.csv")) {
      for (var r : csv)
        jdbc.update(
            "INSERT INTO transacciones(id,fecha,monto,tipo) VALUES(?,?,?,?)",
            Long.parseLong(r.get("id")),
            Date.valueOf(LocalDate.parse(r.get("fecha"))),
            new BigDecimal(r.get("monto")),
            r.get("tipo"));
    }
    try (var csv = leer("cuentas_anuales_migradas.csv")) {
      for (var r : csv)
        jdbc.update(
            "INSERT INTO movimientos_anuales(id,cuenta_id,fecha,transaccion,monto,descripcion)"
                + " VALUES(?,?,?,?,?,?)",
            Long.parseLong(r.get("id")),
            Long.parseLong(r.get("cuenta_id")),
            Date.valueOf(LocalDate.parse(r.get("fecha"))),
            r.get("transaccion"),
            new BigDecimal(r.get("monto")),
            r.get("descripcion"));
    }
    jdbc.update("INSERT INTO migracion_lote(id) VALUES(?)", lote);
    log.info(
        "MIGRACION completada: cuentas={}, transacciones={}, movimientos_anuales={}",
        jdbc.queryForObject("SELECT COUNT(*) FROM cuentas", Long.class),
        jdbc.queryForObject("SELECT COUNT(*) FROM transacciones", Long.class),
        jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_anuales", Long.class));
  }

  private CSVParser leer(String archivo) throws IOException {
    return CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .get()
        .parse(
            new InputStreamReader(
                new ClassPathResource("migrados/" + archivo).getInputStream(),
                StandardCharsets.UTF_8));
  }
}
