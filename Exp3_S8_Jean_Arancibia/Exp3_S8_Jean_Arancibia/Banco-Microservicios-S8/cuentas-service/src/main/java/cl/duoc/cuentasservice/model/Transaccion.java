package cl.duoc.cuentasservice.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaccion(long id, LocalDate fecha, BigDecimal monto, String tipo) {}
