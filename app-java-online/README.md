# CardDemo online re-platform

Spring Boot 3 / Java 17 REST API (`api/`) and React + TypeScript front end (`web/`) replacing the
CardDemo CICS online programs. See [plan.md](plan.md) for the schema, endpoint and screen mapping.

## Docker Compose

From the repository root, `docker compose up --build` starts PostgreSQL, the API (with sample data
loaded) and the web front end on http://localhost:8081. See the root README section
"Online Re-platform (Java + React)" for details and the COBOL-to-endpoint-to-page table.

## API

Requires Java 17 and Docker (for the Testcontainers PostgreSQL used by the tests).

```bash
cd api
./mvnw verify                       # unit + integration tests
CARDDEMO_LOAD_DATA=true ./mvnw spring-boot:run   # needs PostgreSQL on localhost:5432 (carddemo/carddemo)
```

| Variable | Default | Purpose |
| --- | --- | --- |
| `CARDDEMO_DB_URL` / `_USER` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/carddemo`, `carddemo`, `carddemo` | database |
| `CARDDEMO_LOAD_DATA` | `false` | import `app/data` into empty tables at startup |
| `CARDDEMO_DATA_DIR` | `../../app/data` | sample data root |
| `CARDDEMO_DATA_ENCODING` | `ASCII` | `ASCII` or `EBCDIC` |
| `CARDDEMO_SESSION_SECRET` | dev-only value | HMAC key for session tokens (set in any shared environment) |

## Web

```bash
cd web
npm ci
npm test          # Vitest
npm run dev       # http://localhost:5173, proxies /api to localhost:8080
```
