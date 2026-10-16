#!/usr/bin/env bash
# Runs create-orders.js at each rate in sequence and saves one JSON summary per run.
# Usage: BASE_URL=http://localhost:8080 EMAIL=demo@fleetflow.dev PASSWORD=... ./run-sweep.sh
# Add 10000 only after sizing the backend and database for that load.
set -euo pipefail
cd "$(dirname "$0")"

: "${BASE_URL:?set BASE_URL}"
: "${EMAIL:?set EMAIL}"
: "${PASSWORD:?set PASSWORD}"
RATES="${RATES:-100 500 1000}"
DURATION="${DURATION:-60s}"
PREALLOCATED_VUS="${PREALLOCATED_VUS:-}"
MAX_VUS="${MAX_VUS:-}"

mkdir -p results
for RATE in $RATES; do
  echo "=== RATE=$RATE DURATION=$DURATION ==="
  k6 run \
    -e BASE_URL="$BASE_URL" -e EMAIL="$EMAIL" -e PASSWORD="$PASSWORD" \
    -e RATE="$RATE" -e DURATION="$DURATION" \
    ${PREALLOCATED_VUS:+-e PREALLOCATED_VUS="$PREALLOCATED_VUS"} \
    ${MAX_VUS:+-e MAX_VUS="$MAX_VUS"} \
    --summary-export "results/rate-${RATE}.json" \
    create-orders.js
  echo
done
echo "Done. Pull p50/p95/p99, actual req/s and error counts from results/rate-*.json into the README table."