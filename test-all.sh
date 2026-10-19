#!/usr/bin/env bash
#
# End-to-end REST smoke test for the three APIs of this project:
#
#   AccountResource   (account module,      port 8081) - /accounts, /lra/accounts
#   TransferResource  (transfer module,     port 8080) - /xfers, XA/2PC
#   TransferResource  (transfer-lra module, port 8090) - /xfers, MicroProfile LRA saga
#
# Start everything first (docker compose up -d).
#
#
# Usage:  ./test-all.sh            # all three APIs
#         ACCOUNT_URL=... ./test-all.sh
#
set -uo pipefail

ACCOUNT_URL="${ACCOUNT_URL:-http://localhost:8081}"
XA_URL="${XA_URL:-http://localhost:8080}"
SAGA_URL="${SAGA_URL:-http://localhost:8090}"
COORDINATOR_URL="${COORDINATOR_URL:-http://localhost:50000/lra-coordinator}"
PARTICIPANT_COORDINATOR_URL="${PARTICIPANT_COORDINATOR_URL:-http://lra-coordinator:8080/lra-coordinator}"

JSON='Content-Type: application/json'

SRC=ACC90       # funded source
DST=ACC91       # empty destination
TMP=ACC92       # throwaway, used for the delete/404 cases

PASSED=0 FAILED=0
LAST_BODY='' LAST_STATUS=''

section() { printf '\n\033[1m=== %s ===\033[0m\n' "$1"; }
pass()    { PASSED=$((PASSED + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail()    { FAILED=$((FAILED + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
note()    { printf '  \033[33m----\033[0m %s\n' "$1"; }

call() {
  local expected=$1 label=$2 raw
  shift 2
  if ! raw=$(curl -sS -w $'\n%{http_code}' "$@" 2>&1); then
    LAST_STATUS=000 LAST_BODY=''
    fail "$label (curl error: $raw)"
    return 1
  fi
  LAST_STATUS=${raw##*$'\n'}
  LAST_BODY=${raw%$'\n'*}
  if [[ $LAST_STATUS == "$expected" ]]; then
    pass "$label -> $LAST_STATUS"
  else
    fail "$label -> expected $expected, got $LAST_STATUS${LAST_BODY:+ | $LAST_BODY}"
  fi
}

expect_balance() {
  local account=$1 want=$2 got
  got=$(curl -sS "$ACCOUNT_URL/accounts/$account" | jq -r '.balance')
  if awk -v a="$got" -v b="$want" 'BEGIN { exit !(a + 0 == b + 0) }'; then
    pass "balance($account) == $want"
  else
    fail "balance($account) expected $want, got ${got:-<none>}"
  fi
}

up() { curl -sS -o /dev/null --max-time 3 "$1" 2>/dev/null; }

balance_of() { curl -sS "$ACCOUNT_URL/accounts/$1" | jq -r '.balance'; }
held_of()    { curl -sS "$ACCOUNT_URL/lra/accounts/$1/availability" | jq -r '.held'; }

expect_delta() {
  local account=$1 before=$2 delta=$3 after
  after=$(balance_of "$account")
  if awk -v a="$after" -v b="$before" -v d="$delta" \
    'BEGIN { x = (a - b) - d; exit !(x < 0.0001 && x > -0.0001) }'; then
    pass "balance($account) moved by $delta (now $after)"
  else
    fail "balance($account) expected ${before}+${delta}, got $after"
  fi
}

expect_held_delta() {
  local account=$1 before=$2 delta=$3 after
  after=$(held_of "$account")
  if awk -v a="$after" -v b="$before" -v d="$delta" \
    'BEGIN { x = (a - b) - d; exit !(x < 0.0001 && x > -0.0001) }'; then
    pass "held($account) moved by $delta (now $after)"
  else
    fail "held($account) expected ${before}+${delta}, got $after"
  fi
}

wait_until() {
  local tries=$1
  shift
  for ((i = 0; i < tries; i++)); do
    "$@" && return 0
    sleep 0.5
  done
  return 1
}

balance_moved() { awk -v a="$(balance_of "$1")" -v b="$2" 'BEGIN { exit !(a + 0 != b + 0) }'; }
held_is()       { awk -v a="$(held_of "$1")"    -v b="$2" 'BEGIN { exit !(a + 0 == b + 0) }'; }

transfer_status() { curl -sS "$SAGA_URL/xfers/$1" | jq -r '.status'; }
status_is()       { [[ $(transfer_status "$1") == "$2" ]]; }
lra_status()    { curl -sS -H "Long-Running-Action: $1" "$SAGA_URL/xfers/lra/status"; }
lra_status_is() { [[ $(lra_status "$1") == "$2" ]]; }
compensated_exists() {
  curl -sS "$SAGA_URL/xfers" \
    | jq -e --argjson amt "$1" '[.[] | select(.amount == $amt and .status == "COMPENSATED")] | length > 0' >/dev/null
}

participant_host() { local u=${PARTICIPANT_COORDINATOR_URL#*://}; printf '%s' "${u%%/*}"; }

start_lra() {
  local headers id body
  headers=$(mktemp)
  body=$(curl -sS -D "$headers" -X POST -H "Host: $(participant_host)" \
    "$COORDINATOR_URL/start?ClientID=$1&TimeLimit=0")
  id=$(awk 'BEGIN { IGNORECASE = 1 } /^location:/ { print $2 }' "$headers" | tr -d '\r')
  [[ -z $id ]] && id=$(tr -d '"\r\n' <<<"$body")
  rm -f "$headers"
  printf '%s' "$id"
}

for_participant() { printf '%s/%s' "$PARTICIPANT_COORDINATOR_URL" "${1##*/}"; }

end_lra() {
  curl -sS -o /dev/null -w '%{http_code}' -X PUT "$COORDINATOR_URL/${1##*/}/$2"
}

command -v jq >/dev/null || { echo "jq is required (sudo apt install jq)"; exit 2; }

# --------------------------------------------------------------------------
section "Account API - $ACCOUNT_URL/accounts"
# --------------------------------------------------------------------------
if ! up "$ACCOUNT_URL/accounts"; then
  note "account service unreachable on $ACCOUNT_URL - skipping all sections"
  exit 1
fi

for a in "$SRC" "$DST" "$TMP"; do
  curl -sS -o /dev/null -X DELETE "$ACCOUNT_URL/accounts/$a"
done

call 201 "POST /accounts (create $SRC, 1000.00)" -X POST -H "$JSON" \
  -d "{\"accountId\":\"$SRC\",\"customerName\":\"Alice Test\",\"balance\":1000.00,\"currency\":\"EUR\"}" \
  "$ACCOUNT_URL/accounts"
call 201 "POST /accounts (create $DST, 0.00)" -X POST -H "$JSON" \
  -d "{\"accountId\":\"$DST\",\"customerName\":\"Bob Test\",\"balance\":0.00,\"currency\":\"EUR\"}" \
  "$ACCOUNT_URL/accounts"
call 201 "POST /accounts (create $TMP, 10.00)" -X POST -H "$JSON" \
  -d "{\"accountId\":\"$TMP\",\"customerName\":\"Temp Test\",\"balance\":10.00,\"currency\":\"EUR\"}" \
  "$ACCOUNT_URL/accounts"

call 200 "GET /accounts/$SRC" "$ACCOUNT_URL/accounts/$SRC"
expect_balance "$SRC" 1000.00
call 200 "GET /accounts (list)" "$ACCOUNT_URL/accounts"

call 200 "PUT /accounts/$SRC (rename owner)" -X PUT -H "$JSON" \
  -d "{\"accountId\":\"$SRC\",\"customerName\":\"Alice Renamed\",\"balance\":1000.00,\"currency\":\"EUR\"}" \
  "$ACCOUNT_URL/accounts/$SRC"
call 200 "GET /accounts/$SRC (confirm rename)" "$ACCOUNT_URL/accounts/$SRC"
[[ $(jq -r '.customerName' <<<"$LAST_BODY") == "Alice Renamed" ]] \
  && pass "customerName updated" || fail "customerName not updated"

call 200 "POST /accounts/$SRC/credit (50.00)" -X POST -H "$JSON" -d '50.00' \
  "$ACCOUNT_URL/accounts/$SRC/credit"
expect_balance "$SRC" 1050.00
call 200 "POST /accounts/$SRC/debit (200.00)" -X POST -H "$JSON" -d '200.00' \
  "$ACCOUNT_URL/accounts/$SRC/debit"
expect_balance "$SRC" 850.00

section "Account API - negative cases"
call 400 "POST credit (-1.00) rejected" -X POST -H "$JSON" -d '-1.00' \
  "$ACCOUNT_URL/accounts/$SRC/credit"
call 400 "POST debit (0.00) rejected" -X POST -H "$JSON" -d '0.00' \
  "$ACCOUNT_URL/accounts/$SRC/debit"
call 400 "POST debit (999999.00) insufficient funds" -X POST -H "$JSON" -d '999999.00' \
  "$ACCOUNT_URL/accounts/$SRC/debit"
call 404 "GET /accounts/NOSUCH" "$ACCOUNT_URL/accounts/NOSUCH"
call 404 "POST credit on unknown account" -X POST -H "$JSON" -d '10.00' \
  "$ACCOUNT_URL/accounts/NOSUCH/credit"
call 415 "POST credit without Content-Type" -X POST -d '10.00' \
  "$ACCOUNT_URL/accounts/$SRC/credit"
expect_balance "$SRC" 850.00   # nothing above moved the balance

call 204 "DELETE /accounts/$TMP" -X DELETE "$ACCOUNT_URL/accounts/$TMP"
call 404 "GET deleted account" "$ACCOUNT_URL/accounts/$TMP"
call 404 "DELETE already-deleted account" -X DELETE "$ACCOUNT_URL/accounts/$TMP"

# --------------------------------------------------------------------------
section "Account LRA participant API - $ACCOUNT_URL/lra/accounts"
# --------------------------------------------------------------------------
call 200 "GET /lra/accounts/$SRC/availability" "$ACCOUNT_URL/lra/accounts/$SRC/availability"
jq -e '.balance and .available != null' >/dev/null <<<"$LAST_BODY" \
  && pass "availability exposes balance/held/available" || fail "unexpected availability payload: $LAST_BODY"

call 412 "POST /lra/accounts/$SRC/debit without LRA context" -X POST -H "$JSON" -d '10.00' \
  "$ACCOUNT_URL/lra/accounts/$SRC/debit"
call 412 "POST /lra/accounts/$SRC/credit without LRA context" -X POST -H "$JSON" -d '10.00' \
  "$ACCOUNT_URL/lra/accounts/$SRC/credit"
expect_balance "$SRC" 850.00   # a refused participant call moves nothing

# --------------------------------------------------------------------------
section "XA Transfer API (2PC) - $XA_URL/xfers"
# --------------------------------------------------------------------------
if ! up "$XA_URL/xfers"; then
  note "XA transfer service unreachable on $XA_URL - skipping this section"
else
  call 200 "POST /xfers ($SRC -> $DST, 100.00)" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"$DST\",\"amount\":100.00,\"currency\":\"EUR\"}" \
    "$XA_URL/xfers"
  XA_ID=$(jq -r '.transferId // empty' <<<"$LAST_BODY")
  [[ -n $XA_ID ]] && pass "transferId returned: $XA_ID" || fail "no transferId in response"
  expect_balance "$SRC" 750.00
  expect_balance "$DST" 100.00

  call 200 "GET /xfers/$XA_ID" "$XA_URL/xfers/$XA_ID"
  call 200 "GET /xfers (list)" "$XA_URL/xfers"

  call 200 "PUT /xfers/$XA_ID (currency -> USD)" -X PUT -H "$JSON" \
    -d "$(curl -sS "$XA_URL/xfers/$XA_ID" | jq '.currency="USD"')" \
    "$XA_URL/xfers/$XA_ID"

  section "XA Transfer API - negative cases (nothing must move)"
  call 400 "POST /xfers insufficient funds" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"$DST\",\"amount\":999999.00,\"currency\":\"EUR\"}" \
    "$XA_URL/xfers"
  call 400 "POST /xfers negative amount" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"$DST\",\"amount\":-5.00,\"currency\":\"EUR\"}" \
    "$XA_URL/xfers"
  call 404 "POST /xfers unknown source" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"NOSUCH\",\"destinationAccountId\":\"$DST\",\"amount\":10.00,\"currency\":\"EUR\"}" \
    "$XA_URL/xfers"
  call 404 "POST /xfers unknown destination" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"NOSUCH\",\"amount\":10.00,\"currency\":\"EUR\"}" \
    "$XA_URL/xfers"
  expect_balance "$SRC" 750.00   # 2PC rolled every failure back
  expect_balance "$DST" 100.00
  call 404 "GET /xfers/NOSUCH" "$XA_URL/xfers/NOSUCH"

  call 204 "DELETE /xfers/$XA_ID" -X DELETE "$XA_URL/xfers/$XA_ID"
  call 404 "GET deleted transfer" "$XA_URL/xfers/$XA_ID"
fi

# --------------------------------------------------------------------------
section "Saga Transfer API (LRA) - $SAGA_URL/xfers"
# --------------------------------------------------------------------------
if ! up "$SAGA_URL/xfers"; then
  note "saga transfer service unreachable on $SAGA_URL - skipping this section"
else
  call 200 "POST /xfers ($SRC -> $DST, 25.00)" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"$DST\",\"amount\":25.00,\"currency\":\"EUR\"}" \
    "$SAGA_URL/xfers"
  SAGA_ID=$(jq -r '.transferId // empty' <<<"$LAST_BODY")
  [[ -n $SAGA_ID ]] && pass "transferId returned: $SAGA_ID" || fail "no transferId in response"
  expect_balance "$SRC" 725.00
  expect_balance "$DST" 125.00

  call 200 "GET /xfers/$SAGA_ID" "$SAGA_URL/xfers/$SAGA_ID"
  call 200 "GET /xfers (list, includes compensated)" "$SAGA_URL/xfers"

  wait_until 10 status_is "$SAGA_ID" COMPLETED
  status_is "$SAGA_ID" COMPLETED \
    && pass "@Complete flipped the transfer to COMPLETED" \
    || fail "@Complete did not settle the transfer: status=$(transfer_status "$SAGA_ID")"

  section "Saga Transfer API - cancellation and compensation"
  call 400 "POST /xfers insufficient funds (saga cancels)" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"$DST\",\"amount\":999999.00,\"currency\":\"EUR\"}" \
    "$SAGA_URL/xfers"
  expect_balance "$SRC" 725.00
  expect_balance "$DST" 125.00
  wait_until 10 compensated_exists 999999.00
  compensated_exists 999999.00 \
    && pass "@Compensate logged the cancelled transfer as COMPENSATED" \
    || fail "no COMPENSATED transfer for the refused amount"

  call 404 "POST /xfers unknown destination (saga cancels)" -X POST -H "$JSON" \
    -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"NOSUCH\",\"amount\":10.00,\"currency\":\"EUR\"}" \
    "$SAGA_URL/xfers"
  expect_balance "$SRC" 725.00

  call 200 "GET availability after settled sagas" "$ACCOUNT_URL/lra/accounts/$SRC/availability"
  HELD=$(jq -r '.held' <<<"$LAST_BODY")
  awk -v h="$HELD" 'BEGIN { exit !(h + 0 == 0) }' \
    && pass "held == 0 (no reservation left dangling)" \
    || fail "held == $HELD, expected 0 - a reservation outlived its saga"

  call 200 "PUT /xfers/$SAGA_ID (currency -> USD)" -X PUT -H "$JSON" \
    -d "$(curl -sS "$SAGA_URL/xfers/$SAGA_ID" | jq '.currency="USD"')" \
    "$SAGA_URL/xfers/$SAGA_ID"
  call 404 "GET /xfers/NOSUCH" "$SAGA_URL/xfers/NOSUCH"
  call 204 "DELETE /xfers/$SAGA_ID" -X DELETE "$SAGA_URL/xfers/$SAGA_ID"
  call 404 "GET deleted transfer" "$SAGA_URL/xfers/$SAGA_ID"

  # ------------------------------------------------------------------------
  section "Saga participant callbacks - @Status and @Forget (transfer-lra)"
  # ------------------------------------------------------------------------
  if ! up "$COORDINATOR_URL"; then
    note "no LRA coordinator on $COORDINATOR_URL - skipping the callback checks"
  else
    CB_LRA=$(start_lra callback-probe)
    [[ -n $CB_LRA ]] && pass "coordinator started LRA: $CB_LRA" || fail "could not start an LRA"

    if [[ -n $CB_LRA ]]; then
      SRC_BEFORE=$(balance_of "$SRC")
      call 412 "POST /xfers with an externally started LRA" -X POST -H "$JSON" \
        -H "Long-Running-Action: $CB_LRA" \
        -d "{\"sourceAccountId\":\"$SRC\",\"destinationAccountId\":\"$DST\",\"amount\":5.00,\"currency\":\"EUR\"}" \
        "$SAGA_URL/xfers"

      wait_until 10 lra_status_is "$CB_LRA" Compensated
      call 200 "GET /xfers/lra/status (@Status)" -H "Long-Running-Action: $CB_LRA" \
        "$SAGA_URL/xfers/lra/status"
      [[ $LAST_BODY == Compensated ]] \
        && pass "@Status reports Compensated for the cancelled LRA" \
        || fail "@Status reported '$LAST_BODY', expected Compensated"
      expect_delta "$SRC" "$SRC_BEFORE" 0   # a refused saga moves no money

      call 200 "DELETE /xfers/lra/forget (@Forget)" -X DELETE \
        -H "Long-Running-Action: $CB_LRA" "$SAGA_URL/xfers/lra/forget"
      call 404 "GET /xfers/lra/status after @Forget" -H "Long-Running-Action: $CB_LRA" \
        "$SAGA_URL/xfers/lra/status"
      compensated_exists 5.00 \
        && pass "@Forget kept the transfer, only dropped its LRA id" \
        || fail "the forgotten transfer vanished from the log"
    fi
  fi
fi

# --------------------------------------------------------------------------
section "Account LRA participant API - driving a saga by hand"
# --------------------------------------------------------------------------
if ! up "$COORDINATOR_URL"; then
  note "no LRA coordinator on $COORDINATOR_URL - skipping this section"
  note "(Compose publishes it on 50000; in dev mode pass COORDINATOR_URL=...)"
else
  SRC_BEFORE=$(balance_of "$SRC")
  DST_BEFORE=$(balance_of "$DST")
  HELD_BEFORE=$(held_of "$SRC")

  LRA=$(start_lra manual-close)
  [[ -n $LRA ]] && pass "coordinator started LRA: $LRA" || fail "could not start an LRA"

  if [[ -n $LRA ]]; then
    LRA_HDR="Long-Running-Action: $(for_participant "$LRA")"

    call 200 "POST /lra/accounts/$SRC/debit (40.00, in LRA)" -X POST -H "$JSON" -H "$LRA_HDR" \
      -d '40.00' "$ACCOUNT_URL/lra/accounts/$SRC/debit"
    # The whole point of the reservation model: the balance has NOT moved yet.
    expect_delta "$SRC" "$SRC_BEFORE" 0
    expect_held_delta "$SRC" "$HELD_BEFORE" 40.00

    call 200 "POST /lra/accounts/$DST/credit (40.00, in LRA)" -X POST -H "$JSON" -H "$LRA_HDR" \
      -d '40.00' "$ACCOUNT_URL/lra/accounts/$DST/credit"
    expect_delta "$DST" "$DST_BEFORE" 0   # money must not appear before the close

    CODE=$(end_lra "$LRA" close)
    [[ $CODE == 200 ]] && pass "coordinator closed the LRA -> $CODE" \
      || fail "close returned $CODE, expected 200"
    wait_until 10 balance_moved "$SRC" "$SRC_BEFORE"
    expect_delta "$SRC" "$SRC_BEFORE" -40.00
    expect_delta "$DST" "$DST_BEFORE" 40.00
    expect_held_delta "$SRC" "$HELD_BEFORE" 0
  fi

  section "Account LRA participant API - cancelling a saga by hand"
  SRC_BEFORE=$(balance_of "$SRC")
  HELD_BEFORE=$(held_of "$SRC")
  LRA=$(start_lra manual-cancel)
  [[ -n $LRA ]] && pass "coordinator started LRA: $LRA" || fail "could not start an LRA"

  if [[ -n $LRA ]]; then
    call 200 "POST /lra/accounts/$SRC/debit (15.00, in LRA)" -X POST -H "$JSON" \
      -H "Long-Running-Action: $(for_participant "$LRA")" -d '15.00' \
      "$ACCOUNT_URL/lra/accounts/$SRC/debit"
    expect_held_delta "$SRC" "$HELD_BEFORE" 15.00

    CODE=$(end_lra "$LRA" cancel)
    [[ $CODE == 200 ]] && pass "coordinator cancelled the LRA -> $CODE" \
      || fail "cancel returned $CODE, expected 200"
    wait_until 10 held_is "$SRC" "$HELD_BEFORE"
    # A cancelled reservation leaves no trace: no debit applied, no hold retained.
    expect_delta "$SRC" "$SRC_BEFORE" 0
    expect_held_delta "$SRC" "$HELD_BEFORE" 0
  fi
fi

# --------------------------------------------------------------------------
section "Summary"
# --------------------------------------------------------------------------
printf '  %d passed, %d failed\n' "$PASSED" "$FAILED"
[[ $FAILED -eq 0 ]]
