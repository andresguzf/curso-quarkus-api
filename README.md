# curso-quarkus-api

API REST para gestionar clientes (`Customer`), construida con **Quarkus 3.40 LTS**, **Java 25**,
**Hibernate ORM con Panache** y **PostgreSQL**. Proyecto del curso de Quarkus.

## Requisitos

- JDK 25
- Docker o Podman corriendo: en desarrollo y en tests, Quarkus descarga y levanta PostgreSQL
  (y pgAdmin en dev) en contenedores; no hace falta instalar nada más
- No hace falta instalar Maven: el proyecto incluye el wrapper `./mvnw` (Maven 3.9.16)

## Stack

| Componente | Versión |
|---|---|
| Java | 25 |
| Quarkus | 3.40.1.1 (LTS) |
| Hibernate ORM + Panache | 7.4.9.Final |
| Driver PostgreSQL | 42.7.13 |
| Tests | JUnit 5 + REST Assured |

Extensiones de Quarkus: REST Jackson, ArC (CDI), Hibernate Validator, SmallRye OpenAPI,
Hibernate ORM Panache y JDBC PostgreSQL.

## Configuración

1. Copia el archivo de ejemplo y, si quieres, cambia las credenciales:

   ```bash
   cp .env.example .env
   ```

   | Variable | Uso |
   |---|---|
   | `DB_URL` | Solo `prod`: URL JDBC de la base real |
   | `DB_USERNAME` | `dev`: usuario del contenedor `quarkusapi-postgres`. `prod`: usuario de la base real |
   | `DB_PASSWORD` | `dev`: contraseña del contenedor `quarkusapi-postgres`. `prod`: contraseña de la base real |
   | `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | Login de pgAdmin |

2. Ten Docker o Podman corriendo. No hay que crear la base de datos ni levantar contenedores a mano.

`.env` está en `.gitignore` y no se debe versionar. La configuración vive en
`src/main/resources/application.properties`:

| Perfil | Base de datos | Esquema |
|---|---|---|
| `dev` (`./mvnw quarkus:dev`) | Contenedor `quarkusapi-postgres` de `compose-devservices.yml` en `localhost:5432`, base `quarkusapi` | `update` (los datos se conservan) |
| `test` (`./mvnw test`) | Contenedor `postgres:18` temporal de Dev Services, en un puerto aleatorio | `drop-and-create` |
| `prod` (jar empaquetado) | Variables de entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `validate` |

## Ejecutar en desarrollo

```bash
./mvnw quarkus:dev
```

- API: http://localhost:8080/api/customers
- Swagger UI: http://localhost:8080/q/swagger-ui
- Dev UI: http://localhost:8080/q/dev-ui

Dev mode aplica los cambios del código en el siguiente request (live reload).

### Contenedores de desarrollo (PostgreSQL + pgAdmin)

`compose-devservices.yml` define dos contenedores. `./mvnw quarkus:dev` los gestiona solo
(Compose Dev Services): si ya están corriendo los reutiliza, si están detenidos los arranca y, si
no existen, descarga las imágenes y los crea.

| Contenedor | Imagen | Puerto | Volumen |
|---|---|---|---|
| `quarkusapi-postgres` | `postgres:18` | `localhost:5432` | `quarkusapi-postgres-data` |
| `quarkusapi-pgadmin` | `dpage/pgadmin4` | http://localhost:5050 | `quarkusapi-pgadmin-data` |

- Al detener la app los contenedores **siguen corriendo** y no se borran.
- Tienen `restart: unless-stopped`: Docker los vuelve a iniciar cuando arranca.
- Los datos viven en volúmenes de Docker con nombre, así que sobreviven a reinicios y a la
  recreación de los contenedores. En dev el esquema usa `update`: crea tablas y columnas nuevas,
  pero no borra datos ni columnas.
- pgAdmin: ingresa con `PGADMIN_EMAIL` y `PGADMIN_PASSWORD`. El servidor **quarkusapi (dev)**
  (grupo *Quarkus*) viene registrado desde `docker/pgadmin/servers.json`; la primera vez pide la
  contraseña, que es `DB_PASSWORD`.

Comandos útiles (desde la raíz; `docker compose` lee el `.env` solo):

```bash
docker compose -f compose-devservices.yml up -d     # levantarlos sin la app
docker compose -f compose-devservices.yml stop      # detenerlos (conserva datos)
docker compose -f compose-devservices.yml down -v   # borrarlos junto con sus datos
```

Si cambias `PGADMIN_EMAIL`, `PGADMIN_PASSWORD` o las credenciales de la base, hay que recrear el
volumen correspondiente (`down -v`), porque solo se aplican al crearlo.

## Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/api/customers` | Lista todos los clientes | 200 |
| GET | `/api/customers/{id}` | Busca por id | 200, 404 |
| GET | `/api/customers/email/{email}` | Busca por email | 200, 404 |
| GET | `/api/customers/search?term=` | Busca por nombre o apellido, sin distinguir mayúsculas | 200, 400 |
| POST | `/api/customers` | Crea un cliente | 201, 400, 409 |
| PUT | `/api/customers/{id}` | Actualiza un cliente | 200, 400, 404, 409 |
| DELETE | `/api/customers/{id}` | Elimina un cliente | 204, 404 |

El email es único: si ya está registrado por otro cliente, la API responde **409**.

### Errores

Todos los errores responden con el mismo JSON (`GlobalExceptionMapper`), con mensajes simples
para el usuario final. `errors` solo aparece en los errores de validación: cada campo que falló,
con la lista de sus mensajes.

```json
{
  "status": 400,
  "error": "Solicitud inválida",
  "message": "Algunos datos no son válidos. Revisa los campos e inténtalo de nuevo.",
  "path": "/api/customers",
  "timestamp": "2026-10-30T14:07:48.317992",
  "errors": {
    "email": ["El email no puede tener más de 150 caracteres", "El email no tiene un formato válido"],
    "name": ["El nombre es obligatorio"]
  }
}
```

| Status | Cuándo |
|---|---|
| 400 | Campos inválidos, body vacío, JSON mal formado o falta `term` en la búsqueda |
| 404 | El cliente no existe o la ruta no existe |
| 405 | Método HTTP no permitido en esa ruta |
| 409 | El email ya está registrado por otro cliente |
| 500 | Error inesperado (el detalle queda solo en el log) |

Ejemplo:

```bash
curl -i -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name": "Ana", "lastName": "Pérez", "email": "ana@ejemplo.com"}'
```

## Arquitectura

Package base: `com.andres.course.claude.quarkus.quarkusapi.app`

```
controllers/   endpoints REST; validan con @Valid y delegan al service
services/      interface XxxService + XxxServiceImpl con la lógica y las transacciones
repositories/  clases Panache (@ApplicationScoped implements PanacheRepository<T>)
entities/      entidades JPA con auditoría createdAt / updatedAt
models/dto/    records usados como request y response
mappers/       conversión explícita entre entidad y DTO
exceptions/    manejo global de errores (GlobalExceptionMapper)
```

- Los controllers inyectan la interface del service y solo trabajan con DTO.
- Las fechas de auditoría se llenan solas con `@PrePersist` y `@PreUpdate` y no viajan en el DTO.
- Para agregar un recurso nuevo: entity → repository → DTO → mapper → service → controller.

## Tests

```bash
./mvnw test
```

- `CustomerServiceTest`: prueba el service contra PostgreSQL real; cada test hace rollback.
- `CustomerControllerTest`: prueba los endpoints por HTTP con REST Assured.

La primera ejecución descarga la imagen `postgres:18` (unos 150 MB).

## Empaquetar y ejecutar

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

El jar usa el perfil `prod`, así que necesita las variables `DB_URL`, `DB_USERNAME` y
`DB_PASSWORD` definidas en el entorno. En `src/main/docker/` están los Dockerfile generados
por Quarkus (JVM y nativo).

## Guías útiles

- [Quarkus REST](https://quarkus.io/guides/rest)
- [Hibernate ORM con Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [Dev Services para bases de datos](https://quarkus.io/guides/databases-dev-services)
- [OpenAPI y Swagger UI](https://quarkus.io/guides/openapi-swaggerui)
- [Validación](https://quarkus.io/guides/validation)
