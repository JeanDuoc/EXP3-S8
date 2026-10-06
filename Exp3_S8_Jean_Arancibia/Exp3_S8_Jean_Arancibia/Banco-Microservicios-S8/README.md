# Banco XYZ

**Autor:** Jean Arancibia

## Objetivo

Este proyecto implementa una API bancaria con Java 17, Maven, Spring Boot y Spring Cloud. Permite consultar cuentas, transacciones y movimientos anuales, calcular intereses y solicitar reportes de forma asíncrona.

## Componentes

| Servicio | Función |
| --- | --- |
| `config-server` | Centraliza la configuración de la API desde `config-repo`. |
| `discovery-server` | Utiliza Eureka para registrar y descubrir los servicios. |
| `auth-server` | Emite tokens OAuth2 y publica las claves públicas para validar su firma. |
| `cuentas-service` | Expone los endpoints bancarios, consulta la base de datos y procesa los reportes. |

Docker Compose organiza estos cuatro servicios junto con MySQL y el broker Artemis. Cada servicio Java tiene su propia imagen, construida mediante un Dockerfile de dos etapas. Los volúmenes conservan la base de datos, los mensajes y las claves del servidor de autorización. OAuth2 se publica en el puerto 19000 y la API en el 8081.

## Propuesta técnica

La propuesta mantiene la API bancaria de la semana 6 y agrega seguridad y despliegue mediante contenedores. OAuth2 centraliza la emisión de tokens y permite asignar permisos según el cliente. Config Server reúne la configuración y Eureka registra los servicios. Docker Compose permite ejecutar todos los componentes con una configuración común y conservar los datos mediante volúmenes.

Se utiliza Resilience4j para limitar las consultas durante una falla y comprobar la recuperación del servicio. JMS con Artemis permite responder a una solicitud de reporte antes de terminar su procesamiento. La outbox conserva los eventos pendientes si falla el envío y el consumidor evita repetir un reporte completado.

La ejecución verificada corresponde a Docker Desktop en un entorno local. La solución prepara los servicios para su despliegue en un entorno Cloud, pero no incluye un despliegue en una nube pública ni alta disponibilidad. En esta versión, el publicador y el consumidor JMS pertenecen a `cuentas-service`.

## Funcionamiento del código

Los controladores reciben las solicitudes HTTP, los servicios aplican la lógica del banco y los repositorios acceden a los datos mediante JDBC. Los endpoints principales permiten consultar `/api/cuentas`, `/api/transacciones` y `/api/reportes/anuales`.

El código Java de cada servicio está en `src/main/java` y sus recursos en `src/main/resources`. En la API, `security` contiene la seguridad OAuth2 y `eventos` contiene el flujo JMS. `config-repo` reúne la configuración centralizada; `datos` conserva los CSV originales; `scripts` contiene la migración y las pruebas; y `postman` contiene la colección de solicitudes.

La seguridad utiliza OAuth2 con Client Credentials y Authorization Code con PKCE S256. La API valida la firma, el emisor, la audiencia y el vencimiento del token JWT. Los scopes y el rol administrativo controlan el acceso a las consultas, los reportes y las operaciones de administración.

Resilience4j protege las consultas bancarias mediante un Circuit Breaker. Ante fallos devuelve HTTP 503 y abre el circuito; luego permite llamadas de prueba para comprobar la recuperación. El perfil `demo` habilita la simulación de fallos utilizada en las pruebas.

Los reportes utilizan JMS con Artemis. La solicitud y el evento se guardan en una misma transacción mediante una outbox. El publicador reintenta los eventos pendientes y el consumidor completa el reporte sin repetir uno ya terminado. Ambos están implementados en `cuentas-service`. La API responde con HTTP 202 y un identificador para consultar el estado en `/api/reportes/solicitudes/{id}`.

El script de migración normaliza fechas, valida registros y elimina duplicados de los CSV originales. El resultado contiene 50 cuentas, 387 transacciones y 524 movimientos anuales. Los datos proceden de [bank_legacy_data](https://github.com/KariVillagran/bank_legacy_data); los originales y su procedencia se conservan en `datos`.

La carpeta `evidencias` contiene los resultados de las pruebas, los registros de Docker y las capturas. La ejecución realizada en Docker verificó 38 peticiones HTTP, el flujo PKCE y la apertura y recuperación del Circuit Breaker.

## Ejecución

Se necesita Docker Desktop iniciado con contenedores Linux, Docker Compose y conexión a Internet para descargar las imágenes y dependencias. La compilación de Java y Maven se realiza dentro de Docker.

Abrir PowerShell en la carpeta `Banco-Microservicios-S8`, donde se encuentra `docker-compose.yaml`. El ZIP incluye el archivo `.env` usado en las pruebas locales. Si no existe, crearlo desde la plantilla:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

En `.env`, comprobar estos valores para que OAuth2 coincida con el puerto publicado en Compose:

```dotenv
OAUTH_ISSUER=http://localhost:19000
AUTH_URL=http://localhost:19000
APP_PROFILES=mysql
```

Construir las imágenes e iniciar todos los componentes:

```powershell
docker compose up --build -d --wait --wait-timeout 420
docker compose ps
```

Los seis contenedores deben aparecer en estado `healthy`. La API está en `http://localhost:8081`, OAuth2 en `http://localhost:19000`, Eureka en `http://localhost:8761`, Config Server en `http://localhost:8888` y la consola de Artemis en `http://localhost:8161`. Las consultas bancarias requieren un token OAuth2 con los permisos correspondientes; la colección de Postman incluye las solicitudes de autenticación y de la API.

Para revisar los registros o detener los contenedores conservando los volúmenes:

```powershell
docker compose logs --tail=100
docker compose down
```
