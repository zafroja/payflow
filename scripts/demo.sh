#!/usr/bin/env bash
# End-to-end walkthrough against the running stack (docker compose up).
# Requires: curl, jq.   Usage: ./scripts/demo.sh [gateway-url]
set -euo pipefail

GW="${1:-http://localhost:8080}"
RUN=$(date +%s)
ALICE="alice$RUN"
BOB="bob$RUN"

step() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
call() { # method path token [body] [extra-header]
  local method=$1 path=$2 token=$3 body=${4:-} extra=${5:-}
  curl -s -w '\nHTTP %{http_code}\n' -X "$method" "$GW$path" \
    -H 'Content-Type: application/json' \
    ${token:+-H "Authorization: Bearer $token"} \
    ${extra:+-H "$extra"} \
    ${body:+-d "$body"}
}
json() { sed '$d'; } # drop the trailing "HTTP nnn" line

step "1. Register two customers (auth-service, BCrypt + JWT)"
ALICE_TOKEN=$(call POST /api/auth/register "" "{\"username\":\"$ALICE\",\"email\":\"$ALICE@example.com\",\"password\":\"Password1!\"}" | json | jq -r .accessToken)
BOB_TOKEN=$(call POST /api/auth/register "" "{\"username\":\"$BOB\",\"email\":\"$BOB@example.com\",\"password\":\"Password1!\"}" | json | jq -r .accessToken)
echo "alice token: ${ALICE_TOKEN:0:25}..."

step "2. No token -> gateway rejects with 401"
call GET /api/accounts/mine ""

step "3. Open accounts (account-service, JPA/Hibernate)"
ALICE_ACC=$(call POST /api/accounts "$ALICE_TOKEN" '{"type":"CHEQUING"}' | json | jq -r .accountNumber)
BOB_ACC=$(call POST /api/accounts "$BOB_TOKEN" '{"type":"CHEQUING"}' | json | jq -r .accountNumber)
echo "alice=$ALICE_ACC  bob=$BOB_ACC"

step "4. Deposit 1000.00 to alice"
call POST "/api/accounts/$ALICE_ACC/deposits" "$ALICE_TOKEN" '{"amount":1000.00}'

step "5. Read alice's account twice: 1st from Postgres, 2nd from Redis (see: redis-cli keys 'payflow:*')"
call GET "/api/accounts/$ALICE_ACC" "$ALICE_TOKEN" >/dev/null
call GET "/api/accounts/$ALICE_ACC" "$ALICE_TOKEN"

step "6. Bob cannot see alice's account (404, not 403: account numbers cannot be probed)"
call GET "/api/accounts/$ALICE_ACC" "$BOB_TOKEN"

TRANSFER="{\"fromAccount\":\"$ALICE_ACC\",\"toAccount\":\"$BOB_ACC\",\"amount\":250.00,\"description\":\"rent\"}"

step "7. Transfer 250.00 alice -> bob (transaction-service -> Feign -> account-service)"
call POST /api/transactions/transfers "$ALICE_TOKEN" "$TRANSFER" "Idempotency-Key: rent-$RUN"

step "8. Same request + same Idempotency-Key -> replayed (200), money moves once"
call POST /api/transactions/transfers "$ALICE_TOKEN" "$TRANSFER" "Idempotency-Key: rent-$RUN" | json | jq '{reference, status, replayed}'

step "9. Same key, different amount -> 422 IDEMPOTENCY_KEY_REUSED"
call POST /api/transactions/transfers "$ALICE_TOKEN" "${TRANSFER/250.00/999.00}" "Idempotency-Key: rent-$RUN"

step "10. Overdraw -> FAILED / 422 INSUFFICIENT_FUNDS"
call POST /api/transactions/transfers "$ALICE_TOKEN" "${TRANSFER/250.00/5000.00}" "Idempotency-Key: big-$RUN"

step "11. Bulk: 5 transfers of 10.00 processed in parallel (CompletableFuture + bounded pool)"
ITEMS=$(for i in 1 2 3 4 5; do printf '{"idempotencyKey":"bulk-%s-%s","transfer":{"fromAccount":"%s","toAccount":"%s","amount":10.00}},' "$RUN" "$i" "$ALICE_ACC" "$BOB_ACC"; done)
call POST /api/transactions/transfers/bulk "$ALICE_TOKEN" "{\"items\":[${ITEMS%,}]}" | json | jq '{submitted, countByOutcome, elapsedMs}'

step "12. Concurrency: 20 simultaneous 10.00 transfers. Row locks keep the balance exact."
seq 1 20 | xargs -P 20 -I{} curl -s -o /dev/null -w '%{http_code} ' -X POST "$GW/api/transactions/transfers" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Idempotency-Key: race-$RUN-{}" \
  -d "{\"fromAccount\":\"$ALICE_ACC\",\"toAccount\":\"$BOB_ACC\",\"amount\":10.00}"
echo
ALICE_BAL=$(call GET "/api/accounts/$ALICE_ACC" "$ALICE_TOKEN" | json | jq -r .balance)
BOB_BAL=$(call GET "/api/accounts/$BOB_ACC" "$BOB_TOKEN" | json | jq -r .balance)
echo "alice=$ALICE_BAL (expected 500.00 if all 20 completed)  bob=$BOB_BAL  sum=$(jq -n "$ALICE_BAL + $BOB_BAL")"

step "13. 30-day summary (Java 8 streams: groupingBy / reducing)"
call GET "/api/transactions/summary?days=30" "$ALICE_TOKEN" | json | jq '{totalTransfers, countByStatus, totalSent, averageSent, topRecipients}'

step "14. Admin freezes bob's account; transfers to it are rejected"
ADMIN_TOKEN=$(call POST /api/auth/login "" '{"username":"admin","password":"Admin@12345"}' | json | jq -r .accessToken)
call POST "/api/accounts/$BOB_ACC/freeze" "$ADMIN_TOKEN" | json | jq '{accountNumber, status}'
call POST /api/transactions/transfers "$ALICE_TOKEN" "${TRANSFER/250.00/1.00}" "Idempotency-Key: frozen-$RUN"

step "15. Customers cannot use admin endpoints (403)"
call GET "/api/accounts?status=ACTIVE" "$ALICE_TOKEN"

printf '\n\033[1;32mDone.\033[0m Eureka dashboard: http://localhost:8761\n'
