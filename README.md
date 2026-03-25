# Flock

Flock is the Spring Boot entry point for ARCTIC. It's where the application boots from and where every single REST endpoint lives. If you're adding a new endpoint, it goes here — no exceptions.

---

## Entry Point

`com.rahman.arctic.flock.SpringEntry` — the `@SpringBootApplication` class.

---

## Controllers

### `/range-api/v1`

**UserRestController**
- `POST /user` — create a new user (admin or regular)
- `POST /login` — authenticate and get a JWT back
- `POST /regularUser` — sanity check endpoint
- `GET /csrf-token` — grab the CSRF token

**ExerciseRestController**
- `POST /exercise` — create a new range exercise
- `GET /exercise` — list all exercises
- `GET /exercise/{name}` — get a specific exercise
- `DELETE /exercise/{name}` — delete an exercise
- `POST /exercise/{name}/build/{domain}` — kick off a build against a hypervisor profile
- `GET /exercise/{name}/graph` — get the graph layout for an exercise
- `POST /exercise/{name}/graph` — save the graph layout

**IcebergNetworkRestController**
- `GET /exercise/{name}/network` — list networks on an exercise
- `POST /exercise/{name}/network` — add a network to an exercise
- `DELETE /exercise/{name}/network/{n_name}` — remove a network

**IcebergHostRestController**
- Host CRUD under `/exercise/{name}/host`

**IcebergRouterRestController**
- Router CRUD under `/exercise/{name}/router`

**ShardProfileRestController** (`/range-api/v1/profile`)
- Provider/profile management, connection testing, config settings

### `/polarbear-api/v1`

**PolarBearRest**
- `POST /attackstep/{exercise}` — create an attack step on an exercise
- `POST /attackstep/{exercise}/{attackstep}` — add a step item to an attack step
- `GET /attackstep/{exercise}` — list attack steps for an exercise

### `/range-api/v1/admin/providers`

**AdminProviderRestController**
- `GET /` — list all loaded providers and their status
- `POST /reload` — reload all providers from disk
- `POST /{name}/reload` — reload a specific provider
- `POST /{name}/disable` — disable a specific provider at runtime

---

## Notes

- All controllers use constructor injection where possible
- `ResourceNotFoundException` is used for 404-style errors throughout
- `ResourceAlreadyExistsException` handles duplicate creation attempts
