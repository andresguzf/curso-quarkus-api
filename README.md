# curso-quarkus-api

API REST para gestionar clientes (`Customer`), construida con **Quarkus 3.40 LTS**, **Java 25**,
**Hibernate ORM con Panache** y **PostgreSQL**. Proyecto del curso de Quarkus.

## Requisitos

- JDK 25
- Docker o Podman corriendo (los tests levantan PostgreSQL en un contenedor)
- PostgreSQL local para el modo desarrollo
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

1. Copia el archivo de ejemplo y completa tus credenciales locales:

   ```bash
   cp .env.example .env
   ```

   | Variable | Uso |
   |---|---|
   | `DB_URL` | URL JDBC, por ejemplo `jdbc:postgresql://localhost:5432/quarkusapi` |
   | `DB_USERNAME` | Usuario de la base de datos |
   | `DB_PASSWORD` | Contraseña de la base de datos |

2. Crea la base de datos `quarkusapi` en tu PostgreSQL local.

`.env` está en `.gitignore` y no se debe versionar. La configuración vive en
`src/main/resources/application.properties`:

| Perfil | Base de datos | Esquema |
|---|---|---|
| `dev` (`./mvnw quarkus:dev`) | PostgreSQL local según `.env` | `drop-and-create` (se recrea al arrancar) |
| `test` (`./mvnw test`) | Contenedor `postgres:18` de Dev Services | `drop-and-create` |
| `prod` (jar empaquetado) | Variables de entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `validate` |

## Ejecutar en desarrollo

```bash
./mvnw quarkus:dev
```

- API: http://localhost:8080/api/customers
- Swagger UI: http://localhost:8080/q/swagger-ui
- Dev UI: http://localhost:8080/q/dev-ui

Dev mode aplica los cambios del código en el siguiente request (live reload).

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
