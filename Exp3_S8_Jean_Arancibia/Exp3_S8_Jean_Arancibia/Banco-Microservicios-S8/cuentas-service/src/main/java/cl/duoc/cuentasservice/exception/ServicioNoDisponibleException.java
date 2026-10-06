package cl.duoc.cuentasservice.exception;

public class ServicioNoDisponibleException extends RuntimeException {
  public ServicioNoDisponibleException(Throwable causa) {
    super(
        "El servicio de datos no se encuentra disponible temporalmente. Intenta nuevamente en unos"
            + " segundos.",
        causa);
  }
}
