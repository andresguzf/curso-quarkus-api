# CLAUDE.md

API REST de clientes (`Customer`) con usuarios y roles, hecha con Quarkus (curso de Quarkus).
Repositorio: https://github.com/andresguzf/curso-quarkus-api

## Stack y versiones

Java 25 · Quarkus 3.40.1.1 LTS · Hibernate ORM 7.4.9.Final (Panache clásico) · PostgreSQL (driver
42.7.13) · Maven 3.9.16 (`./mvnw`) · Tests: JUnit 5, REST Assured y `quarkus-test-security`.

Extensiones: `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`,
`quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`,
`quarkus-elytron-security-common` (bcrypt) y `quarkus-rest-csrf`. Packaging `jar`. Config solo en `application.properties`.

## Comandos

```bash
./mvnw quarkus:dev            # dev mode; Swagger UI en /q/swagger-ui, Dev UI en /q/dev-ui
./mvnw test                   # tests (levanta PostgreSQL con Docker)
./mvnw package -DskipTests    # jar en target/quarkus-app/
```

## Estructura

Package base: `com.andres.course.claude.quarkus.quarkusapi.app`

```
controllers/   CustomerController, UserController (/api/users), AuthController (/api/auth)
services/      XxxService (interface) + XxxServiceImpl (@ApplicationScoped, @Transactional)
repositories/  CustomerRepository, UserRepository: clase @ApplicationScoped implements PanacheRepository
entities/      Customer, User (tabla users), Role (enum USER/ADMIN + constantes Role.Names)
models/dto/    CustomerDto, UserDto (password WRITE_ONLY), LoginDto, ErrorDto, PageDto,
               CustomerFilter y UserFilter (query params de paginación, orden y filtros)
mappers/       CustomerMapper, UserMapper: conversión explícita, nunca copian el password
security/      PasswordHasher (bcrypt), TokenService (firma el JWT), AdminSeeder (admin inicial)
exceptions/    GlobalExceptionMapper (todo error es ErrorDto) y EmptyErrorResponseFilter (400 de CSRF)
```

## Convenciones de arquitectura

- Orden para un recurso nuevo: entity → repository → DTO → mapper → service → controller.
- **Controllers**: solo REST, `@Valid @NotNull` en el body (nombrado como el recurso), inyectan la
  *interface* del service y devuelven DTO. Cada endpoint lleva `@PermitAll`, `@Authenticated` o
  `@RolesAllowed`: `deny-unannotated-endpoints=true` cierra los que no tengan anotación.
- **Services**: interface + `Impl`; `@Transactional` en escrituras. Errores con mensaje simple:
  `NotFoundException` (404), `ClientErrorException` `CONFLICT` (409), `NotAuthorizedException` (401).
- **Repositories**: una sola clase Panache, sin interface ni `Impl`.
- **Entities**: no extienden `PanacheEntity`; `id` `Long` `IDENTITY`; `createdAt`/`updatedAt` con
  `@PrePersist`/`@PreUpdate`, solo con getter.
- **DTO**: un `record` por recurso, Bean Validation con mensajes en español. Sin datos sensibles.
  Excepción a la skill, pedida para el frontend: `createdAt`/`updatedAt` viajan como
  `@JsonProperty(access = READ_ONLY)` (se devuelven, se ignoran al recibir); un constructor sin
  fechas mantiene `new XxxDto(id, ...)`. El password de `UserDto` es `@JsonProperty(access = WRITE_ONLY)` y se exige
  al crear con el grupo `UserDto.OnCreate` (`@ConvertGroup`).
- **Mappers**: `toDto`, `toEntity`, `updateEntity` a mano; el hash del password lo hace el service.
- **Listados paginados**: `GET` de la colección recibe un record de filtro con `@Valid @BeanParam`
  (`page` desde 0, `size` 1-100, `sort` y `dir` validados con lista blanca), el repository arma la
  consulta con `search(filter)` solo con los filtros que llegan, como parámetros, y el service
  devuelve `PageDto.of(...)`. El orden desempata por `id` para que las páginas sean estables.
- Inyección por constructor.

## Seguridad

- Roles: `user` solo lee clientes (`GET`); `admin` hace el CRUD de clientes y de usuarios.
- Login (`POST /api/auth/login`) deja el JWT en la cookie `access_token` (`HttpOnly`, `SameSite=Strict`,
  `Secure` en prod, 1 hora). smallrye-jwt la lee con `mp.jwt.token.header=Cookie`.
- CSRF (double submit): `GET /api/auth/csrf` entrega la cookie `csrf-token`; todo POST/PUT/DELETE,
  incluidos login y registro, repite su valor en `X-CSRF-TOKEN`. Sin ella: 400 JSON.
- Registro público (`POST /api/auth/register`) siempre crea rol `user`. El primer admin lo crea
  `AdminSeeder` con `ADMIN_EMAIL`/`ADMIN_PASSWORD` si no existe ninguno.
- Reglas de usuarios: email único, un admin no se elimina a sí mismo, siempre queda al menos un admin.
- Claves JWT: en dev y test Quarkus genera un par en memoria; en prod `JWT_PRIVATE_KEY_LOCATION` y
  `JWT_PUBLIC_KEY_LOCATION`. `quarkus.http.auth.proactive=false` para que los 401 pasen por el mapper.

## Base de datos y perfiles

- **dev**: `compose-devservices.yml` con `quarkusapi-postgres` (5432) y `quarkusapi-pgadmin` (5050);
  `quarkus:dev` los crea o arranca y no los detiene. Volúmenes `quarkusapi-*-data`. Esquema `update`.
  No definir `jdbc.url` en dev: desactiva Dev Services.
- **test**: sin compose; Dev Services crea un `postgres:18` temporal; `drop-and-create`; sin admin semilla.
- **prod**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; esquema `validate`.
- `.env` está en `.gitignore`: nunca versionarlo ni escribir credenciales reales en el código.

## Tests

- Services: `@QuarkusTest` + `@TestTransaction` (rollback por test).
- Controllers: REST Assured con `@TestSecurity` (admin por defecto) y `Csrf.given()`, que obtiene y
  reenvía el token CSRF. Limpian sus tablas en `@BeforeEach`/`@AfterEach`.
- `AuthFlowTest`: flujo real sin `@TestSecurity` (registro, login con cookie, roles, CSRF, logout).

## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `quarkus-best-practices` | `.claude/skills/quarkus-best-practices/` | Genera el proyecto desde code.quarkus.io (Java 25, Maven, Quarkus LTS >= 3.40.1, jar) y crea o modifica sus capas: controllers, services (interface + Impl), repositories Panache, entities, DTO record, mappers y manejo global de errores (`GlobalExceptionMapper` + `ErrorDto`). Agrega bajo demanda Panache + PostgreSQL (`add-persistence`) y seguridad con JWT en cookie HttpOnly, roles y CSRF (`add-security`). |

La carpeta es un symlink a `../2-quarkus-arc/.claude/skills/quarkus-best-practices`, la única
fuente de la verdad: cualquier cambio al skill se hace en `2-quarkus-arc`, nunca copiando archivos aquí.

## Skill trigger rules

- Usa `quarkus-best-practices` cuando el usuario pida crear una API básica, un microservicio o un proyecto con Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida crear, agregar o modificar un entity, DTO, mapper, repository, service, controller o endpoint REST.
- Usa `quarkus-best-practices` cuando el usuario pida agregar persistencia (Hibernate ORM Panache, PostgreSQL), por comando (`add-persistence`) o en lenguaje natural ("quiero agregar Panache con Postgres"). Este proyecto ya la tiene.
- Usa `quarkus-best-practices` cuando el usuario pida seguridad (login, usuarios, roles, JWT, CSRF) o `add-security`. Este proyecto ya la tiene.
- Antes de escribir una capa, lee `references/layer-templates.md` del skill (y `references/security-templates.md` para la seguridad).
- Para ejecutar la aplicación localmente usa siempre `./mvnw quarkus:dev`.
