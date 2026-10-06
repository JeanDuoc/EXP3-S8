package cl.duoc.cuentasservice.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoAnual(
    long id,
    long cuentaId,
    LocalDate fecha,
    String transaccion,
    BigDecimal monto,
    String descripcion) {}
