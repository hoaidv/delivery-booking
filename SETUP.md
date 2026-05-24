# delivery-booking

Multi-module Spring Boot project for delivery booking (Booking Service + Booking Worker).

## Prerequisites

| Requirement | Version / notes |
|-------------|-----------------|
| **JDK** | 21 (matches the Gradle Java toolchain) |
| **Gradle** | Use the included wrapper (`./gradlew`) |
| **Kafka** | Required when Kafka integrations are enabled |
| **Redis** | Required when cache integrations are enabled |
| **PostgreSQL / ScyllaDB** | Required when persistence is wired (see `.scripts/docker-compose-local.yml`) |

## Modules

| Module | Role |
|--------|------|
| `dbcommon` | Shared models, repository interfaces, Kafka DTOs, SQL schemas |
| `dbservice` | REST API (Booking Service) |
| `dbworker` | Kafka consumer (Booking Worker) |

## Build

From the project root:

```bash
./gradlew build
```

Runnable JARs:

- `dbservice/build/libs/dbservice-0.0.1-SNAPSHOT.jar`
- `dbworker/build/libs/dbworker-0.0.1-SNAPSHOT.jar`

## Run locally

### Booking Service (HTTP API)

```bash
./gradlew :dbservice:bootRun
```

Default HTTP port: `8080`.

### Booking Worker

```bash
./gradlew :dbworker:bootRun
```

No port needed.

## Project layout

```
dbcommon/     Shared domain, repositories, infra stubs, schema SQL
dbservice/    present (REST), appcore, infra
dbworker/     listener, appcore, infra
build.gradle  Root aggregator + shared subproject config
```

Schema files:

- `dbcommon/src/main/resources/schema-cassandra.sql`
- `dbcommon/src/main/resources/schema-postgres.sql`

## Troubleshooting

- **Java version errors:** Install JDK 21 or set `JAVA_HOME` to a JDK 21 installation.
- **Port already in use:** Set `server.port` in `dbservice/src/main/resources/application.properties`.
- **Datastore connection failures:** Start local dependencies with Docker Compose under `.scripts/`.

## Dependency helpers

To quickly bootstrap some dependencies for local development, please use [docker-compose](.scripts/docker-compose-local.yml).