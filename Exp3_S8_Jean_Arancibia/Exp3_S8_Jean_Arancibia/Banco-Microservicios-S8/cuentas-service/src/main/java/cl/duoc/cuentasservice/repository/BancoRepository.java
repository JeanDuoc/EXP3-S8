package cl.duoc.cuentasservice.repository;

import cl.duoc.cuentasservice.config.FalloSimulado;
import cl.duoc.cuentasservice.model.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BancoRepository {
  private final JdbcTemplate jdbc;
  private final ObjectProvider<FalloSimulado> fallo;

  public BancoRepository(JdbcTemplate jdbc, ObjectProvider<FalloSimulado> fallo) {
    this.jdbc = jdbc;
    this.fallo = fallo;
  }

  private void verificar() {
    fallo.ifAvailable(FalloSimulado::verificar);
  }

  public List<Cuenta> cuentas() {
    verificar();
    return jdbc.query(
        "SELECT * FROM cuentas ORDER BY cuenta_id",
        (r, n) ->
            new Cuenta(
                r.getLong("cuenta_id"),
                r.getString("nombre"),
                r.getBigDecimal("saldo"),
                r.getInt("edad"),
                r.getString("tipo"),
                r.getBigDecimal("interes_calculado"),
                r.getBigDecimal("saldo_final")));
  }

  public Optional<Cuenta> cuenta(long id) {
    verificar();
    return jdbc
        .query(
            "SELECT * FROM cuentas WHERE cuenta_id=?",
            (r, n) ->
                new Cuenta(
                    r.getLong("cuenta_id"),
                    r.getString("nombre"),
                    r.getBigDecimal("saldo"),
                    r.getInt("edad"),
                    r.getString("tipo"),
                    r.getBigDecimal("interes_calculado"),
                    r.getBigDecimal("saldo_final")),
            id)
        .stream()
        .findFirst();
  }

  public List<Transaccion> transacciones(int limite, int offset) {
    verificar();
    return jdbc.query(
        "SELECT * FROM transacciones ORDER BY id LIMIT ? OFFSET ?",
        (r, n) ->
            new Transaccion(
                r.getLong("id"),
                r.getDate("fecha").toLocalDate(),
                r.getBigDecimal("monto"),
                r.getString("tipo")),
        limite,
        offset);
  }

  public Optional<Transaccion> transaccion(long id) {
    verificar();
    return jdbc
        .query(
            "SELECT * FROM transacciones WHERE id=?",
            (r, n) ->
                new Transaccion(
                    r.getLong("id"),
                    r.getDate("fecha").toLocalDate(),
                    r.getBigDecimal("monto"),
                    r.getString("tipo")),
            id)
        .stream()
        .findFirst();
  }

  public List<MovimientoAnual> anuales(Long cuentaId, int limite, int offset) {
    verificar();
    var mapper =
        (org.springframework.jdbc.core.RowMapper<MovimientoAnual>)
            (r, n) ->
                new MovimientoAnual(
                    r.getLong("id"),
                    r.getLong("cuenta_id"),
                    r.getDate("fecha").toLocalDate(),
                    r.getString("transaccion"),
                    r.getBigDecimal("monto"),
                    r.getString("descripcion"));
    return cuentaId == null
        ? jdbc.query(
            "SELECT * FROM movimientos_anuales ORDER BY id LIMIT ? OFFSET ?",
            mapper,
            limite,
            offset)
        : jdbc.query(
            "SELECT * FROM movimientos_anuales WHERE cuenta_id=? ORDER BY id LIMIT ? OFFSET ?",
            mapper,
            cuentaId,
            limite,
            offset);
  }

  public Map<String, Long> conteos() {
    verificar();
    return Map.of(
        "cuentas",
        jdbc.queryForObject("SELECT COUNT(*) FROM cuentas", Long.class),
        "transacciones",
        jdbc.queryForObject("SELECT COUNT(*) FROM transacciones", Long.class),
        "movimientosAnuales",
        jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_anuales", Long.class));
  }
}
