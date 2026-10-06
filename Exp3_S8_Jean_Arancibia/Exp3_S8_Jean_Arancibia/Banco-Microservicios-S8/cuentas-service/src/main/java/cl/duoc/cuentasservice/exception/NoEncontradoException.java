package cl.duoc.cuentasservice.exception;

public class NoEncontradoException extends RuntimeException {
  public NoEncontradoException(String mensaje) {
    super(mensaje);
  }
}
