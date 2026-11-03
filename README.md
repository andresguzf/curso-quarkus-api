# curso-quarkus-api

API REST para gestionar clientes (`Customer`), construida con **Quarkus 3.40 LTS**, **Java 25**,
**Hibernate ORM con Panache** y **PostgreSQL en Supabase**. Proyecto del curso de Quarkus.

## Requisitos

- JDK 25
- Un proyecto en [Supabase](https://supabase.com) (PostgreSQL administrado): `dev` y `prod` se conectan ahí
- Docker o Podman corriendo solo para los tests: Quarkus levanta un PostgreSQL temporal
- No hace falta instalar Maven: el proyecto incluye el wrapper `./mvnw` (Maven 3.9.16)

## Stack

| Componente | Versión |
|---|---|
| Java | 25 |
| Quarkus | 3.40.1.1 (LTS) |
| Hibernate ORM + Panache | 7.4.9.Final |
| Driver PostgreSQL | 42.7.13 |
| Base de datos | PostgreSQL en Supabase (Session pooler) |
| Tests | JUnit 5 + REST Assured + Quarkus Test Security |

Extensiones de Quarkus: REST Jackson, ArC (CDI), Hibernate Validator, SmallRye OpenAPI,
Hibernate ORM Panache, JDBC PostgreSQL, SmallRye JWT (verificación y firma), Elytron Security
(bcrypt) y REST CSRF.

## Configuración

1. Copia el archivo de ejemplo:

   ```bash
   cp .env.example .env
   ```

2. En Supabase abre **Connect > JDBC > Session pooler** y completa el `.env`:

   | Variable | Uso |
   |---|---|
   | `DB_URL` | URL JDBC del Session pooler con `?sslmode=require`, sin usuario ni contraseña |
   | `DB_USERNAME` | Usuario del pooler: `postgres.<project-ref>` |
   | `DB_PASSWORD` | Contraseña de la base de Supabase |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Admin inicial de la API: se crea al arrancar si no existe ningún admin |
   | `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION` | Solo `prod`: claves PEM para firmar y verificar el JWT |
   | `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | Solo si usas el compose local (ver abajo) |

   Usa el **Session pooler** y no la conexión directa: la directa solo funciona por IPv6 en el plan
   gratuito.

`.env` está en `.gitignore` y no se debe versionar. La configuración vive en
`src/main/resources/application.properties`:

| Perfil | Base de datos | Esquema |
|---|---|---|
| `dev` (`./mvnw quarkus:dev`) | Supabase, con `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` del `.env` | `update` (crea tablas y columnas nuevas, no borra datos) |
| `test` (`./mvnw test`) | Contenedor `postgres:18` temporal de Dev Services, en un puerto aleatorio | `drop-and-create` |
| `prod` (jar empaquetado) | Supabase, con las variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` del entorno | `validate` |

Los tests nunca tocan Supabase: `drop-and-create` borraría las tablas en cada ejecución.

## Ejecutar en desarrollo

```bash
./mvnw quarkus:dev
```

- API: http://localhost:8080/api/customers
- Swagger UI: http://localhost:8080/q/swagger-ui
- Dev UI: http://localhost:8080/q/dev-ui

Dev mode aplica los cambios del código en el siguiente request (live reload). Los datos se ven y
editan desde el **Table Editor** de Supabase.

### Compose local (desactivado)

`compose-devservices.yml` (PostgreSQL 18 + pgAdmin) era la base de desarrollo antes de Supabase. Queda
en el repo como referencia, pero `quarkus.compose.devservices.enabled=false` evita que `quarkus:dev`
lo levante. Sus datos viven en el volumen `quarkusapi-postgres-data`.

Para pasar sus datos a Supabase (con las tablas ya creadas por un primer `quarkus:dev`), sin instalar
`psql`, desde la raíz:

```bash
export PGUSER="$(grep '^DB_USERNAME=' .env | cut -d= -f2-)" PGPASSWORD="$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)"
{ echo "TRUNCATE customers, users RESTART IDENTITY;"; docker exec quarkusapi-postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d quarkusapi --data-only --column-inserts -t customers -t users' | grep -v '^SET transaction_timeout'; } \
  | docker run --rm -i -e PGUSER -e PGPASSWORD postgres:18 psql "host=<host-del-pooler> port=5432 dbname=postgres sslmode=require" --single-transaction -v ON_ERROR_STOP=1
```

Vacía las tablas de Supabase, copia las filas y deja las secuencias en el último id, todo en una
transacción.

## Endpoints

Todos los endpoints de `/api/customers` y `/api/users` requieren sesión (ver [Seguridad](#seguridad)).

| Método | Ruta | Descripción | Rol | Respuestas |
|---|---|---|---|---|
| GET | `/api/customers` | Lista paginada con orden y filtros (ver abajo) | user, admin | 200, 400 |
| GET | `/api/customers/{id}` | Busca por id | user, admin | 200, 404 |
| GET | `/api/customers/email/{email}` | Busca por email | user, admin | 200, 404 |
| GET | `/api/customers/search?term=` | Busca por nombre o apellido, sin distinguir mayúsculas | user, admin | 200, 400 |
| POST | `/api/customers` | Crea un cliente | admin | 201, 400, 409 |
| PUT | `/api/customers/{id}` | Actualiza un cliente | admin | 200, 400, 404, 409 |
| DELETE | `/api/customers/{id}` | Elimina un cliente | admin | 204, 404 |
| GET | `/api/users` | Lista paginada con orden y filtros (ver abajo) | admin | 200, 400 |
| GET | `/api/users/{id}` | Busca un usuario | admin | 200, 404 |
| POST | `/api/users` | Crea un usuario con el rol indicado (`user` por defecto) | admin | 201, 400, 409 |
| PUT | `/api/users/{id}` | Edita email, rol y, si se envía, el password | admin | 200, 400, 404, 409 |
| DELETE | `/api/users/{id}` | Elimina un usuario | admin | 204, 404, 409 |

El email es único: si ya está registrado por otro cliente, la API responde **409**.

### Paginación, orden y filtros

`GET /api/customers` y `GET /api/users` devuelven una página:

```json
{"content": [ ... ], "page": 0, "size": 10, "totalElements": 57, "totalPages": 6}
```

| Query param | Clientes | Usuarios | Por defecto |
|---|---|---|---|
| `page` | página, desde 0 | igual | `0` |
| `size` | elementos por página, 1 a 100 | igual | `10` |
| `sort` | `name`, `lastName`, `email`, `createdAt` | `email`, `role`, `createdAt` | `createdAt` |
| `dir` | `asc`, `desc` | igual | `desc` |
| `name`, `lastName`, `email` | contiene, sin distinguir mayúsculas | solo `email` | — |
| `role` | — | `user`, `admin` | — |
| `createdFrom`, `createdTo` | fecha de alta `YYYY-MM-DD`, ambos días incluidos | igual | — |

Un `sort`, `dir`, `role` o `size` inválido responde **400** con el detalle en `errors`.
Ejemplo: `GET /api/customers?page=0&size=5&lastName=gu&sort=createdAt&dir=desc`.

Los clientes y usuarios incluyen `createdAt` y `updatedAt` (solo lectura: se ignoran si se envían).

## Seguridad

Autenticación con **JWT en una cookie HttpOnly**, autorización por **roles** y protección **CSRF**, con
las extensiones nativas de Quarkus (smallrye-jwt, Quarkus Security, Elytron bcrypt y rest-csrf).

| Rol | Puede |
|---|---|
| `user` | Iniciar sesión y consultar clientes (`GET`) |
| `admin` | Todo lo anterior, más el CRUD completo de clientes y de usuarios |

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/auth/csrf` | público | Entrega la cookie `csrf-token` (204) |
| POST | `/api/auth/register` | público | Registra un usuario; siempre con rol `user` |
| POST | `/api/auth/login` | público | Valida email y password, y deja el JWT en la cookie `access_token` |
| POST | `/api/auth/logout` | público | Borra la cookie de sesión |
| GET | `/api/auth/me` | público | Devuelve el usuario de la sesión, o 204 si no hay sesión |

**Cómo funciona:**

1. **CSRF (double submit):** el frontend pide `GET /api/auth/csrf` y recibe la cookie `csrf-token`. En cada
   `POST`, `PUT` o `DELETE` (incluidos login y registro) repite su valor en la cabecera `X-CSRF-TOKEN`.
   Si falta o no coincide: **400**. Esa cookie no es `HttpOnly` porque el frontend tiene que leerla.
2. **Login:** responde el usuario y la cookie `access_token` con el JWT: `HttpOnly` (JavaScript no puede
   leerla), `SameSite=Strict` (el navegador no la envía desde otros sitios), `Secure` en prod y 1 hora
   de duración. El navegador la envía sola en cada petición.
3. **Autorización:** `@RolesAllowed` en cada endpoint. Sin sesión: **401**; con un rol sin permiso: **403**.
   Los endpoints sin anotación de seguridad quedan cerrados (`deny-unannotated-endpoints`).
4. **Passwords:** se guardan como hash bcrypt y nunca aparecen en una respuesta. El login responde lo mismo
   si el email no existe o si el password es incorrecto.
5. **Admin inicial:** al arrancar, si no hay ningún admin, se crea con `ADMIN_EMAIL` y `ADMIN_PASSWORD`.
   No se puede eliminar al último admin ni quitarle el rol, y un admin no puede eliminarse a sí mismo.

En dev y test, Quarkus genera en memoria el par de claves que firma el JWT, así que las sesiones se
pierden al reiniciar la app. En prod hay que definir `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION`.

Ejemplo con `curl`, guardando las cookies en un archivo (`-c` guarda, `-b` envía):

```bash
curl -s -c cookies.txt http://localhost:8080/api/auth/csrf
TOKEN=$(grep csrf-token cookies.txt | awk '{print $7}')
curl -s -c cookies.txt -b cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $TOKEN" \
  -d '{"email": "admin@example.com", "password": "tu-password-admin"}'
curl -s -b cookies.txt http://localhost:8080/api/customers
```

Swagger UI usa la cookie de sesión del navegador, así que sirve para las consultas después de iniciar
sesión. Para los `POST`, `PUT` y `DELETE` hay que enviar la cabecera CSRF, que Swagger UI no agrega sola.

## Errores

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
| 400 | Campos inválidos, body vacío, JSON mal formado, falta `term` en la búsqueda o falta el token CSRF |
| 401 | Sin sesión, sesión expirada o login con credenciales incorrectas |
| 403 | El rol no permite la operación |
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
exceptions/    manejo global de errores (GlobalExceptionMapper y el JSON del rechazo CSRF)
security/      hash bcrypt, firma del JWT y creación del admin inicial
```

- Los controllers inyectan la interface del service y solo trabajan con DTO.
- Las fechas de auditoría se llenan solas con `@PrePersist` y `@PreUpdate` y viajan en el DTO solo como lectura.
- Para agregar un recurso nuevo: entity → repository → DTO → mapper → service → controller.

## Tests

```bash
./mvnw test
```

- `CustomerServiceTest`: prueba el service contra PostgreSQL real; cada test hace rollback.
- `UserServiceTest`: registro, hash del password, login y las reglas del último admin.
- `CustomerControllerTest` y `UserControllerTest`: prueban los endpoints por HTTP con REST Assured,
  con `@TestSecurity` para elegir el rol y el token CSRF en cada petición.
- `AuthFlowTest`: el flujo real sin atajos: registro, login con cookie HttpOnly, permisos por rol,
  rechazo sin token CSRF y logout.

La primera ejecución descarga la imagen `postgres:18` (unos 150 MB).

## Empaquetar y ejecutar

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

El jar usa el perfil `prod`, así que necesita las variables `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION` definidas en el entorno.
En prod el esquema se valida (`validate`): las tablas `customers` y `users` deben existir antes. En `src/main/docker/` están los Dockerfile generados
por Quarkus (JVM y nativo).

## Guías útiles

- [Quarkus REST](https://quarkus.io/guides/rest)
- [Hibernate ORM con Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [Dev Services para bases de datos](https://quarkus.io/guides/databases-dev-services)
- [OpenAPI y Swagger UI](https://quarkus.io/guides/openapi-swaggerui)
- [Validación](https://quarkus.io/guides/validation)
- [JWT con SmallRye JWT](https://quarkus.io/guides/security-jwt)
- [Autorización en endpoints](https://quarkus.io/guides/security-authorize-web-endpoints-reference)
- [Protección CSRF](https://quarkus.io/guides/security-csrf-prevention)
