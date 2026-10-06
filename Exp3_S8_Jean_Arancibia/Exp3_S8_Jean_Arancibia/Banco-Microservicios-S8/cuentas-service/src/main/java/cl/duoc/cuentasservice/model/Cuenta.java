package cl.duoc.cuentasservice.model;

import java.math.BigDecimal;

public record Cuenta(
    long cuentaId,
    String nombre,
    BigDecimal saldo,
    int edad,
    String tipo,
    BigDecimal interesCalculado,
    BigDecimal saldoFinal) {}
