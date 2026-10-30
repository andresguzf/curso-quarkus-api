# CLAUDE.md

API REST de clientes (`Customer`) hecha con Quarkus, parte del curso de Quarkus.
Repositorio: https://github.com/andresguzf/curso-quarkus-api

## Stack y versiones

| Componente | Versión |
|---|---|
| Java | 25 (`maven.compiler.release`) |
| Quarkus (plataforma LTS) | 3.40.1.1 |
| Hibernate ORM (con Panache clásico) | 7.4.9.Final |
| Driver PostgreSQL | 42.7.13 |
| Maven (wrapper `./mvnw`) | 3.9.16 |
| Tests | JUnit 5 (`quarkus-junit`) + REST Assured |

Extensiones: `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-hibernate-validator`,
`quarkus-smallrye-openapi`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`.
Packaging `jar` (fast-jar en `target/quarkus-app/`). Configuración solo en `application.properties`.

## Comandos

```bash
./mvnw quarkus:dev                      # dev mode: live reload, Dev UI en /q/dev-ui
./mvnw test                             # tests (levanta PostgreSQL con Docker)
./mvnw package -DskipTests              # compila el jar
java -jar target/quarkus-app/quarkus-run.jar
```

Swagger UI: http://localhost:8080/q/swagger-ui

## Estructura

Package base: `com.andres.course.claude.quarkus.quarkusapi.app`

```
controllers/   CustomerController        endpoints REST en /api/customers
services/      CustomerService           interface (contrato de negocio)
               CustomerServiceImpl       @ApplicationScoped, lógica y @Transactional
repositories/  CustomerRepository        clase @ApplicationScoped implements PanacheRepository
entities/      Customer                  @Entity, tabla customers
models/dto/    CustomerDto               record usado como request y response
mappers/       CustomerMapper            conversión explícita entity <-> dto
exceptions/    GlobalExceptionMapper     @ServerExceptionMapper: todo error sale como ErrorDto JSON
```

Tests en `src/test/java/.../app/` con la misma estructura (`services/`, `controllers/`).

## Convenciones de arquitectura

- Orden para un recurso nuevo: entity → repository → DTO → mapper → service → controller.
- **Controllers**: solo REST, validan con `@Valid`, inyectan la *interface* del service y
  devuelven DTO. Nunca usan repositorios ni entidades.
- **Services**: interface `XxxService` + `XxxServiceImpl` `@ApplicationScoped`. Se inyecta
  siempre la interface. `@Transactional` en las escrituras. Errores: `NotFoundException` (404) y
  `ClientErrorException` `CONFLICT` (409) con mensaje simple; `GlobalExceptionMapper` arma el JSON.
- **Repositories**: una sola clase `@ApplicationScoped` que implementa `PanacheRepository<T>`,
  sin interface ni `Impl`. Consultas propias como métodos públicos.
- **Entities**: no extienden `PanacheEntity`. `id` `Long` con `IDENTITY`. Auditoría con
  `createdAt` / `updatedAt` llenadas por `@PrePersist` / `@PreUpdate`, solo con getter.
- **DTO**: un `record` por recurso; excluye fechas de auditoría y datos sensibles.
  Bean Validation va en el record.
- **Mappers**: `toDto`, `toEntity`, `updateEntity` a mano (sin MapStruct); nunca copian
  `id` ni fechas de auditoría.
- Inyección por constructor en services, mappers y controllers.

## Base de datos y perfiles

- **dev**: `compose-devservices.yml` (Compose Dev Services) con `quarkusapi-postgres` (5432) y
  `quarkusapi-pgadmin` (5050). `quarkus:dev` los crea o arranca solo y no los detiene
  (`stop-services=false`, sin Ryuk). Datos en volúmenes `quarkusapi-*-data`. Esquema `update`.
  No definir `jdbc.url` en dev: desactiva Dev Services.
- **test**: compose desactivado; Dev Services crea un `postgres:18` temporal. `drop-and-create`.
- **prod**: `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` como variables de entorno; esquema `validate`.
- `.env` está en `.gitignore`: nunca versionarlo ni escribir credenciales reales en el código.
- pgAdmin en http://localhost:5050 (`PGADMIN_EMAIL`/`PGADMIN_PASSWORD`); servidor en `docker/pgadmin/servers.json`.

## Tests

- `CustomerServiceTest`: `@QuarkusTest` + `@TestTransaction` (rollback por test).
- `CustomerControllerTest`: REST Assured; cada request confirma su transacción, por eso
  limpia la tabla en `@BeforeEach` y `@AfterEach` para no afectar otras clases.
- Requieren Docker o Podman corriendo.

## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `quarkus-best-practices` | `.claude/skills/quarkus-best-practices/` | Genera el proyecto desde code.quarkus.io (Java 25, Maven, Quarkus LTS >= 3.40.1, jar) y crea o modifica sus capas: controllers, services (interface + Impl), repositories Panache, entities, DTO record, mappers y manejo global de errores (`GlobalExceptionMapper` + `ErrorDto`). Agrega Panache + PostgreSQL bajo demanda (`add-persistence`). |

La carpeta es un symlink a `../2-quarkus-arc/.claude/skills/quarkus-best-practices`, la única
fuente de la verdad: cualquier cambio al skill se hace en `2-quarkus-arc`, nunca copiando archivos aquí.

## Skill trigger rules

- Usa `quarkus-best-practices` cuando el usuario pida crear una API básica, un microservicio o un proyecto con Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida crear, agregar o modificar un entity, DTO, mapper, repository, service, controller o endpoint REST.
- Usa `quarkus-best-practices` cuando el usuario pida agregar persistencia (Hibernate ORM Panache, PostgreSQL), por comando (`add-persistence`) o en lenguaje natural ("quiero agregar Panache con Postgres"). Este proyecto ya la tiene.
- Antes de escribir una capa, lee `references/layer-templates.md` del skill.
- Para ejecutar la aplicación localmente usa siempre `./mvnw quarkus:dev`.
