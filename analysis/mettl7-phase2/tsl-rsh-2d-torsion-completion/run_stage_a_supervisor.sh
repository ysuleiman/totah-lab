#!/bin/bash
set -euo pipefail

TOKEN_FILE=/Users/yazan/access_tokens/runpod/acces_token
KEY=/Users/yazan/.ssh/codex_10_0_0_241
KNOWN=/private/tmp/tsl_stagea_wrapper_known_hosts
CREATE_JSON=/private/tmp/tsl-stage-a-wrapper-create.json
LOCAL_ROOT=${LOCAL_ROOT:-/Users/yazan/totah-lab/analysis/mettl7-phase2/tsl-rsh-2d-torsion-completion/stage-a-live-evidence}
PKG=/opt/stage-a/TSL_RSH_PHIPSI_2D_GPU_PACKAGE
REMOTE_BASE=${REMOTE_BASE:-/workspace/tsl-stage-a}
RATE=${RATE:-1.59}
TIMEOUT=${TIMEOUT:-1200}
MAX_COST=${MAX_COST:-35}
CANARY_MODE=${CANARY_MODE:-false}
POD_ID=${POD_ID:-hn837ck13cwsjs}
HOST=${HOST:-185.216.23.121}
PORT=${PORT:-22177}
STAGE_START=$(date +%s)

mkdir -p "$LOCAL_ROOT/results" "$LOCAL_ROOT/logs"
printf '%s\n' "$STAGE_START" > "$LOCAL_ROOT/STAGE_A_START_EPOCH"

api() {
  local method=$1 url=$2
  local token
  token=$(tr -d '\r\n' < "$TOKEN_FILE")
  curl -fsS -X "$method" -H "Authorization: Bearer $token" "$url"
}

ssh_cmd() {
  ssh -o ConnectTimeout=15 -o StrictHostKeyChecking=no -o UserKnownHostsFile="$KNOWN" -i "$KEY" -p "$PORT" "root@$HOST" "$@"
}

sync_results() {
  rsync -az --delete -e "ssh -o ConnectTimeout=15 -o StrictHostKeyChecking=no -o UserKnownHostsFile=$KNOWN -i $KEY -p $PORT" \
    "root@$HOST:$REMOTE_BASE/results/" "$LOCAL_ROOT/results/"
  [ -f "$LOCAL_ROOT/results/production/WAVEFRONT_STATE.json" ] || { echo 'checkpoint synchronization produced no wavefront state' >&2; return 1; }
}

wait_for_ssh() {
  local limit=$(( $(date +%s) + 600 ))
  while ! ssh_cmd 'true' >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "$limit" ]; then return 1; fi
    sleep 10
  done
}

recreate_pod() {
  api DELETE "https://rest.runpod.io/v1/pods/$POD_ID" >/dev/null || true
  local token response
  token=$(tr -d '\r\n' < "$TOKEN_FILE")
  response=$(curl -fsS -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data-binary "@$CREATE_JSON" https://rest.runpod.io/v1/pods)
  POD_ID=$(jq -r .id <<<"$response")
  while :; do
    sleep 10
    response=$(curl -fsS -H "Authorization: Bearer $token" "https://rest.runpod.io/v1/pods/$POD_ID")
    HOST=$(jq -r '.publicIp // empty' <<<"$response")
    PORT=$(jq -r '.portMappings["22"] // empty' <<<"$response")
    [ -n "$HOST" ] && [ -n "$PORT" ] && break
  done
  wait_for_ssh
  ssh_cmd "mkdir -p $REMOTE_BASE/results"
  rsync -az -e "ssh -o ConnectTimeout=15 -o StrictHostKeyChecking=no -o UserKnownHostsFile=$KNOWN -i $KEY -p $PORT" \
    "$LOCAL_ROOT/results/" "root@$HOST:$REMOTE_BASE/results/"
  scp -o StrictHostKeyChecking=no -o UserKnownHostsFile="$KNOWN" -i "$KEY" -P "$PORT" \
    /Users/yazan/totah-lab/analysis/mettl7-phase2/tsl-rsh-2d-torsion-completion/STAGE_A_AUTHORIZATION_RECEIPT.json \
    "root@$HOST:$REMOTE_BASE/"
  printf '%s pod=%s host=%s port=%s\n' "$(date -u +%FT%TZ)" "$POD_ID" "$HOST" "$PORT" >> "$LOCAL_ROOT/RECREATIONS.log"
}

attempt=0
while :; do
  now=$(date +%s)
  elapsed=$((now-STAGE_START))
  cost=$(awk -v s="$elapsed" -v r="$RATE" 'BEGIN{printf "%.8f",s/3600*r}')
  if awk -v c="$cost" -v m="$MAX_COST" 'BEGIN{exit !(c>=m)}'; then
    sync_results || true
    api DELETE "https://rest.runpod.io/v1/pods/$POD_ID" >/dev/null || true
    printf 'BUDGET_STOP cost=%s\n' "$cost" | tee -a "$LOCAL_ROOT/SUPERVISOR.log"
    exit 35
  fi
  if [ -f "$LOCAL_ROOT/results/PRODUCTION_RESULT.json" ]; then
    printf 'COMPLETE cost=%s\n' "$cost" | tee -a "$LOCAL_ROOT/SUPERVISOR.log"
    break
  fi
  attempt=$((attempt+1))
  log="$LOCAL_ROOT/logs/attempt_$(printf '%04d' "$attempt").log"
  printf '%s attempt=%d pod=%s cost=%s\n' "$(date -u +%FT%TZ)" "$attempt" "$POD_ID" "$cost" | tee -a "$LOCAL_ROOT/SUPERVISOR.log"
  ssh_cmd "cd $PKG && TSL_RESULTS_ROOT=$REMOTE_BASE/results python3 run_2d_gpu.py --production-step --authorization-receipt $REMOTE_BASE/STAGE_A_AUTHORIZATION_RECEIPT.json --reuse-benchmark-root $REMOTE_BASE/tsl-rsh-benchmark/results" >"$log" 2>&1 &
  pid=$!
  started=$(date +%s)
  timed_out=false
  while kill -0 "$pid" 2>/dev/null; do
    sleep 10
    if [ $(( $(date +%s)-started )) -ge "$TIMEOUT" ]; then
      timed_out=true
      printf '%s attempt=%d RUNTIME_TIMEOUT\n' "$(date -u +%FT%TZ)" "$attempt" | tee -a "$LOCAL_ROOT/SUPERVISOR.log"
      kill "$pid" 2>/dev/null || true
      if [ "$CANARY_MODE" = true ]; then
        sync_results || true
        api DELETE "https://rest.runpod.io/v1/pods/$POD_ID" >/dev/null || true
        exit 71
      fi
      recreate_pod
      break
    fi
  done
  if [ "$timed_out" = true ]; then continue; fi
  if ! wait "$pid"; then
    sync_results || true
    api DELETE "https://rest.runpod.io/v1/pods/$POD_ID" >/dev/null || true
    printf '%s attempt=%d RUNTIME_INTEGRITY_FAILURE\n' "$(date -u +%FT%TZ)" "$attempt" | tee -a "$LOCAL_ROOT/SUPERVISOR.log"
    exit 70
  fi
  sync_results
  ssh_cmd "cd $PKG && TSL_RESULTS_ROOT=$REMOTE_BASE/results python3 run_2d_gpu.py --production-status --authorization-receipt $REMOTE_BASE/STAGE_A_AUTHORIZATION_RECEIPT.json" | tee -a "$LOCAL_ROOT/SUPERVISOR.log"
done

sync_results
api DELETE "https://rest.runpod.io/v1/pods/$POD_ID" >/dev/null
printf '%s\n' "$POD_ID" > "$LOCAL_ROOT/FINAL_POD_ID"
printf '%s\n' "$(date +%s)" > "$LOCAL_ROOT/STAGE_A_END_EPOCH"
