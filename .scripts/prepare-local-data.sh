#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/.scripts/docker-compose-local.yml"
KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-delivery-booking}"

POSTGRES_USER="${POSTGRES_USER:-delivery}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-delivery}"
POSTGRES_DB="${POSTGRES_DB:-delivery_booking}"
KAFKA_TOPIC="db.delivery.booking.events"
KAFKA_RETENTION_MS=$((3 * 24 * 60 * 60 * 1000))

POSTGRES_SCHEMA="${ROOT_DIR}/dbcommon/src/main/resources/schema-postgres.sql"
SCYLLA_SCHEMA="${ROOT_DIR}/dbcommon/src/main/resources/schema-cassandra.sql"

compose() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

wait_for_service() {
  local service="$1"
  local max_attempts="${2:-60}"
  local attempt=1

  echo "Waiting for ${service}..."
  while [ "${attempt}" -le "${max_attempts}" ]; do
    if compose ps --status running --services | grep -qx "${service}" \
      && compose exec -T "${service}" true 2>/dev/null; then
      case "${service}" in
        postgres)
          if compose exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
            echo "${service} is ready."
            return 0
          fi
          ;;
        scylladb)
          if compose exec -T scylladb cqlsh -e "describe cluster" >/dev/null 2>&1; then
            echo "${service} is ready."
            return 0
          fi
          ;;
        kafka)
          if compose exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh \
            --bootstrap-server localhost:9092 >/dev/null 2>&1; then
            echo "${service} is ready."
            return 0
          fi
          ;;
        redis)
          if compose exec -T redis redis-cli ping 2>/dev/null | grep -qx PONG; then
            echo "${service} is ready."
            return 0
          fi
          ;;
        *)
          echo "${service} is ready."
          return 0
          ;;
      esac
    fi
    sleep 2
    attempt=$((attempt + 1))
  done

  echo "Timed out waiting for ${service}."
  exit 1
}

wait_for_kind_cluster() {
  local max_attempts="${1:-60}"
  local expected_nodes=3
  local attempt=1

  echo "Checking kind cluster '${KIND_CLUSTER_NAME}' in Docker..."

  if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running."
    exit 1
  fi

  if ! docker ps -a \
    --filter "label=io.x-k8s.kind.cluster=${KIND_CLUSTER_NAME}" \
    -q 2>/dev/null | grep -q .; then
    echo "Kind cluster '${KIND_CLUSTER_NAME}' was not found in this Docker instance."
    echo "Kind runs as Docker containers labeled io.x-k8s.kind.cluster=${KIND_CLUSTER_NAME}."
    echo "Create it with: ./.scripts/setup-kind.sh"
    exit 1
  fi

  while [ "${attempt}" -le "${max_attempts}" ]; do
    local running_nodes
    running_nodes=$(
      docker ps \
        --filter "label=io.x-k8s.kind.cluster=${KIND_CLUSTER_NAME}" \
        --filter "status=running" \
        -q 2>/dev/null | wc -l | tr -d ' '
    )

    if [ "${running_nodes}" -ge "${expected_nodes}" ]; then
      if command -v kubectl >/dev/null 2>&1; then
        local ready_nodes
        ready_nodes=$(
          kubectl get nodes --context "kind-${KIND_CLUSTER_NAME}" --no-headers 2>/dev/null \
            | grep -c " Ready" || true
        )
        if [ "${ready_nodes}" -ge "${expected_nodes}" ]; then
          echo "kind cluster '${KIND_CLUSTER_NAME}' is ready in Docker (${ready_nodes} nodes Ready)."
          return 0
        fi
      else
        echo "kind cluster '${KIND_CLUSTER_NAME}' is running in Docker (${running_nodes} node containers)."
        return 0
      fi
    fi

    if [ "${attempt}" -eq 1 ] && [ "${running_nodes}" -eq 0 ]; then
      echo "Kind node containers exist but none are running. Try: docker start \$(docker ps -a --filter label=io.x-k8s.kind.cluster=${KIND_CLUSTER_NAME} -q)"
    fi

    sleep 2
    attempt=$((attempt + 1))
  done

  echo "Timed out waiting for kind cluster '${KIND_CLUSTER_NAME}' in Docker."
  echo "Inspect node containers: docker ps -a --filter label=io.x-k8s.kind.cluster=${KIND_CLUSTER_NAME}"
  exit 1
}

schema_has_statements() {
  local schema_file="$1"
  grep -Ev '^\s*(--|$)' "${schema_file}" >/dev/null 2>&1
}

create_kafka_topic() {
  echo "Creating Kafka topic '${KAFKA_TOPIC}'..."
  compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --create \
    --if-not-exists \
    --bootstrap-server localhost:9092 \
    --topic "${KAFKA_TOPIC}" \
    --partitions 3 \
    --config "retention.ms=${KAFKA_RETENTION_MS}"

  compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --describe \
    --bootstrap-server localhost:9092 \
    --topic "${KAFKA_TOPIC}"
}

apply_postgres_schema() {
  if ! schema_has_statements "${POSTGRES_SCHEMA}"; then
    echo "PostgreSQL schema file is empty; skipping."
    return 0
  fi

  echo "Applying PostgreSQL schema from ${POSTGRES_SCHEMA}..."
  compose exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    < "${POSTGRES_SCHEMA}"
}

apply_scylla_schema() {
  if ! schema_has_statements "${SCYLLA_SCHEMA}"; then
    echo "ScyllaDB schema file is empty; skipping."
    return 0
  fi

  echo "Applying ScyllaDB schema from ${SCYLLA_SCHEMA}..."
  compose exec -T scylladb cqlsh < "${SCYLLA_SCHEMA}"
}

main() {
  if ! docker compose version >/dev/null 2>&1; then
    echo "docker compose is not available."
    exit 1
  fi

  if ! compose ps --services >/dev/null 2>&1; then
    echo "Starting local dependencies..."
    compose up -d
  elif [ "$(compose ps --status running -q 2>/dev/null | wc -l | tr -d ' ')" -eq 0 ]; then
    echo "Starting local dependencies..."
    compose up -d
  fi

  wait_for_service redis 30
  wait_for_service postgres 30
  wait_for_service kafka 60
  wait_for_service scylladb 90
  wait_for_kind_cluster 60

  create_kafka_topic
  apply_postgres_schema
  apply_scylla_schema

  echo
  echo "Local development data is ready."
}

main "$@"
