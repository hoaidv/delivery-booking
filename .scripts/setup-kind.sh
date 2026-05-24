#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-delivery-booking}"
CONFIG_FILE="${ROOT_DIR}/.scripts/kind-config.yaml"
EXPECTED_NODES=3

if ! command -v kind >/dev/null 2>&1; then
  echo "kind is not installed. Install it from https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is not installed. Install it to verify the kind cluster."
  exit 1
fi

wait_for_nodes_ready() {
  local max_attempts="${1:-60}"
  local attempt=1

  echo "Waiting for kind nodes to become Ready..."
  while [ "${attempt}" -le "${max_attempts}" ]; do
    local ready_nodes
    ready_nodes=$(
      kubectl get nodes --context "kind-${CLUSTER_NAME}" --no-headers 2>/dev/null \
        | grep -c " Ready" || true
    )

    if [ "${ready_nodes}" -ge "${EXPECTED_NODES}" ]; then
      return 0
    fi

    sleep 2
    attempt=$((attempt + 1))
  done

  echo "Timed out waiting for kind nodes to become Ready."
  kubectl get nodes --context "kind-${CLUSTER_NAME}" || true
  exit 1
}

if kind get clusters 2>/dev/null | grep -qx "${CLUSTER_NAME}"; then
  echo "Kind cluster '${CLUSTER_NAME}' already exists."
else
  kind create cluster --name "${CLUSTER_NAME}" --config "${CONFIG_FILE}"
fi

wait_for_nodes_ready 60

echo
echo "Kind cluster '${CLUSTER_NAME}' is ready with ${EXPECTED_NODES} nodes:"
kubectl get nodes --context "kind-${CLUSTER_NAME}"
kubectl cluster-info --context "kind-${CLUSTER_NAME}"
