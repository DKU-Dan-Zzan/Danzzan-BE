#!/usr/bin/env bash

set -euo pipefail

INPUT_CSV=""
EXPECTED_SUCCESS="${EXPECTED_SUCCESS:-1000}"
EXPECTED_SOLD_OUT="${EXPECTED_SOLD_OUT:-500}"
EXPECTED_ALREADY="${EXPECTED_ALREADY:-0}"

usage() {
  cat <<'USAGE'
Usage: scripts/aggregate_ticket_claim_results.sh --input <csv> [options]

Options:
  --input PATH              input csv from loadtest_ticket_claim.sh (required)
  --expected-success N      expected SUCCESS count (default: 1000)
  --expected-sold-out N     expected SOLD_OUT count (default: 500)
  --expected-already N      expected ALREADY count (default: 0)
  -h, --help                show help
USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --input)
        INPUT_CSV="$2"
        shift 2
        ;;
      --expected-success)
        EXPECTED_SUCCESS="$2"
        shift 2
        ;;
      --expected-sold-out)
        EXPECTED_SOLD_OUT="$2"
        shift 2
        ;;
      --expected-already)
        EXPECTED_ALREADY="$2"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        echo "[aggregate] unknown argument: $1" >&2
        usage
        exit 1
        ;;
    esac
  done
}

validate() {
  [[ -n "$INPUT_CSV" ]] || { echo "[aggregate] --input is required" >&2; exit 1; }
  [[ -f "$INPUT_CSV" ]] || { echo "[aggregate] input not found: $INPUT_CSV" >&2; exit 1; }
  [[ "$EXPECTED_SUCCESS" =~ ^[0-9]+$ ]] || { echo "[aggregate] expected-success must be integer" >&2; exit 1; }
  [[ "$EXPECTED_SOLD_OUT" =~ ^[0-9]+$ ]] || { echo "[aggregate] expected-sold-out must be integer" >&2; exit 1; }
  [[ "$EXPECTED_ALREADY" =~ ^[0-9]+$ ]] || { echo "[aggregate] expected-already must be integer" >&2; exit 1; }
}

main() {
  parse_args "$@"
  validate

  local total success sold_out already other_status
  local http_error curl_error request_error
  total="$(awk -F, 'NR>1 {c++} END {print c+0}' "$INPUT_CSV")"
  success="$(awk -F, 'NR>1 && $5=="SUCCESS" {c++} END {print c+0}' "$INPUT_CSV")"
  sold_out="$(awk -F, 'NR>1 && $5=="SOLD_OUT" {c++} END {print c+0}' "$INPUT_CSV")"
  already="$(awk -F, 'NR>1 && $5=="ALREADY" {c++} END {print c+0}' "$INPUT_CSV")"
  other_status="$(awk -F, 'NR>1 && $5!="SUCCESS" && $5!="SOLD_OUT" && $5!="ALREADY" {c++} END {print c+0}' "$INPUT_CSV")"

  http_error="$(awk -F, 'NR>1 && ($3<200 || $3>299) {c++} END {print c+0}' "$INPUT_CSV")"
  curl_error="$(awk -F, 'NR>1 && $7!="0" {c++} END {print c+0}' "$INPUT_CSV")"
  request_error="$(awk -F, 'NR>1 && ($7!="0" || $3<200 || $3>299) {c++} END {print c+0}' "$INPUT_CSV")"

  local diff_success diff_sold_out diff_already
  diff_success=$((success - EXPECTED_SUCCESS))
  diff_sold_out=$((sold_out - EXPECTED_SOLD_OUT))
  diff_already=$((already - EXPECTED_ALREADY))

  local http_error_rate request_error_rate
  if (( total > 0 )); then
    http_error_rate="$(awk -v e="$http_error" -v t="$total" 'BEGIN {printf "%.4f", (e/t)*100}')"
    request_error_rate="$(awk -v e="$request_error" -v t="$total" 'BEGIN {printf "%.4f", (e/t)*100}')"
  else
    http_error_rate="0.0000"
    request_error_rate="0.0000"
  fi

  echo "input_csv=$INPUT_CSV"
  echo "total_requests=$total"
  echo "status_success=$success (expected=$EXPECTED_SUCCESS, diff=$diff_success)"
  echo "status_sold_out=$sold_out (expected=$EXPECTED_SOLD_OUT, diff=$diff_sold_out)"
  echo "status_already=$already (expected=$EXPECTED_ALREADY, diff=$diff_already)"
  echo "status_other=$other_status"
  echo "http_error_count=$http_error http_error_rate_pct=$http_error_rate"
  echo "curl_error_count=$curl_error"
  echo "request_error_count=$request_error request_error_rate_pct=$request_error_rate"
}

main "$@"
