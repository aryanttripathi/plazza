#!/usr/bin/env bash
#
# End-to-end test sheet for the ride hailing backend.
#
# Fires every endpoint in demo order and prints the request, the HTTP status, and the response
# body for each one. Read top to bottom during the demo: each section is one requirement or one
# edge case from the problem statement.
#
# Usage:
#   ./scripts/e2e.sh                 # against http://localhost:8080
#   BASE=http://host:9090 ./scripts/e2e.sh
#   RESET_DB=1 ./scripts/e2e.sh      # wipe the tables first (needs MYSQL_PASSWORD)
#
# The app must already be running:
#   ./mvnw spring-boot:run
#
# Requires: curl, jq.

set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
PASS=0
FAIL=0

# --- pretty output -----------------------------------------------------------------------------
if [ -t 1 ]; then
    BOLD=$(printf '\033[1m'); DIM=$(printf '\033[2m'); RED=$(printf '\033[31m')
    GREEN=$(printf '\033[32m'); CYAN=$(printf '\033[36m'); OFF=$(printf '\033[0m')
else
    BOLD=""; DIM=""; RED=""; GREEN=""; CYAN=""; OFF=""
fi

section() {
    printf '\n%s══ %s %s\n' "$BOLD$CYAN" "$*" "$OFF"
}

# call <expected-status> <description> <METHOD> <path> [json-body]
# Prints the call, asserts the status code, and pretty-prints the response.
# The response body is left in RESPONSE for the caller to pull ids out of.
call() {
    local expected="$1" description="$2" method="$3" path="$4" body="${5:-}"
    local status raw

    if [ -n "$body" ]; then
        raw=$(curl -s -w '\n%{http_code}' -X "$method" "$BASE$path" \
              -H 'Content-Type: application/json' -d "$body")
    else
        raw=$(curl -s -w '\n%{http_code}' -X "$method" "$BASE$path")
    fi

    status="${raw##*$'\n'}"
    RESPONSE="${raw%$'\n'*}"

    printf '\n%s%s%s\n' "$BOLD" "$description" "$OFF"
    printf '%s  %s %s%s\n' "$DIM" "$method" "$path" "$OFF"
    [ -n "$body" ] && printf '%s  → %s%s\n' "$DIM" "$body" "$OFF"

    if [ "$status" = "$expected" ]; then
        printf '  %s✓ %s%s  ' "$GREEN" "$status" "$OFF"
        PASS=$((PASS + 1))
    else
        printf '  %s✗ %s (expected %s)%s  ' "$RED" "$status" "$expected" "$OFF"
        FAIL=$((FAIL + 1))
    fi

    if [ -n "$RESPONSE" ]; then
        echo "$RESPONSE" | jq -c . 2>/dev/null || echo "$RESPONSE"
    else
        echo "(no body)"
    fi
}

id_of() { echo "$RESPONSE" | jq -r .id; }

# --- preflight ---------------------------------------------------------------------------------
if ! curl -sf -o /dev/null "$BASE/api/coupons"; then
    printf '%sCannot reach %s — start the app first:%s\n' "$RED" "$BASE" "$OFF"
    printf '  ./mvnw spring-boot:run\n'
    exit 1
fi

if [ "${RESET_DB:-0}" = "1" ]; then
    mysql -u"${MYSQL_USER:-root}" -p"${MYSQL_PASSWORD:?set MYSQL_PASSWORD to reset the database}" \
          -D plazza -e "DELETE FROM rides; DELETE FROM drivers; DELETE FROM users; DELETE FROM coupons;" 2>/dev/null \
        && echo "database reset"
fi

# Unique phone numbers so repeat runs do not collide on the uk_users_phone index.
STAMP=$(date +%s)
PHONE_A="9${STAMP: -9}"
PHONE_B="8${STAMP: -9}"

# MG Road, Bengaluru
PICKUP='"pickup":{"lat":12.9716,"lng":77.5946}'
# Koramangala, 6.001 km away — crosses all three slabs but lands under the sedan minimum
NEAR_DROP='"drop":{"lat":12.9279,"lng":77.6271}'
# Electronic City, ~15.75 km — clear of the floor, so slab arithmetic shows in the total
FAR_DROP='"drop":{"lat":12.8452,"lng":77.6602}'
# ~250 m — the minimum-fare case
HOP_DROP='"drop":{"lat":12.9740,"lng":77.5970}'

printf '%sRide Hailing Backend — end-to-end sheet%s\n' "$BOLD" "$OFF"
printf '%starget: %s%s\n' "$DIM" "$BASE" "$OFF"

# --- 1. pricing engine, no booking required ----------------------------------------------------
section "1. Pricing engine (tiered slabs, minimum fare, car types)"

call 200 "7 km sedan — 2x10 + 3x8 + 2x5 = 54.00" \
     GET "/api/fare/estimate?distanceKm=7&carType=SEDAN"

call 200 "3 km sedan — slabs give 28.00, minimum fare floors it to 50.00" \
     GET "/api/fare/estimate?distanceKm=3&carType=SEDAN"

call 200 "7 km hatchback — cheaper rate card, 42.00" \
     GET "/api/fare/estimate?distanceKm=7&carType=HATCHBACK"

call 200 "16 km sedan — 2x10 + 3x8 + 11x5 = 99.00, well clear of the floor" \
     GET "/api/fare/estimate?distanceKm=16&carType=SEDAN"

call 400 "unknown car type is rejected with the valid options" \
     GET "/api/fare/estimate?distanceKm=7&carType=TRUCK"

# --- 2. registration ---------------------------------------------------------------------------
section "2. Register riders and drivers"

call 201 "register a rider" POST "/api/users" \
     "{\"name\":\"Aryant\",\"phone\":\"$PHONE_A\"}"
USER_A=$(id_of)

call 201 "register a second rider (for the concurrency race)" POST "/api/users" \
     "{\"name\":\"Second Rider\",\"phone\":\"$PHONE_B\"}"
USER_B=$(id_of)

call 400 "duplicate phone is rejected" POST "/api/users" \
     "{\"name\":\"Impostor\",\"phone\":\"$PHONE_A\"}"

call 400 "blank name and malformed phone report per-field errors" POST "/api/users" \
     '{"name":"   ","phone":"abc"}'

call 201 "register a sedan ~0.5 km away (car type sent lower case on purpose)" POST "/api/drivers" \
     '{"name":"Ravi","carType":"sedan","rating":4.8,"lat":12.9750,"lng":77.5980}'
SEDAN=$(id_of)

call 201 "register a hatchback ~1.2 km away" POST "/api/drivers" \
     '{"name":"Suresh","carType":"HATCHBACK","rating":4.2,"lat":12.9800,"lng":77.6020}'
HATCH=$(id_of)

call 201 "register a sedan ~21 km away — outside any sane radius" POST "/api/drivers" \
     '{"name":"Distant","carType":"SEDAN","rating":5.0,"lat":13.1400,"lng":77.7000}'

call 400 "unknown car type is rejected" POST "/api/drivers" \
     '{"name":"Bad","carType":"TRUCK","rating":4.0,"lat":12.97,"lng":77.59}'

call 400 "latitude out of range is rejected" POST "/api/drivers" \
     '{"name":"Bad","carType":"SEDAN","rating":4.0,"lat":99.9,"lng":77.59}'

call 404 "unknown driver is a 404" GET "/api/drivers/no-such-driver"

# --- 3. location and status --------------------------------------------------------------------
section "3. Update a cab's location and availability"

call 200 "move the sedan closer to the pickup point" PATCH "/api/drivers/$SEDAN/location" \
     '{"lat":12.9730,"lng":77.5960}'

call 200 "take the hatchback offline" PATCH "/api/drivers/$HATCH/status" \
     '{"status":"OFFLINE"}'

call 400 "ON_TRIP cannot be set by hand — booking owns that transition" \
     PATCH "/api/drivers/$SEDAN/status" '{"status":"ON_TRIP"}'

# --- 4. coupons --------------------------------------------------------------------------------
section "4. Coupons"

call 201 "add a 20% coupon capped at 50 (code and type sent lower case)" POST "/api/coupons" \
     '{"code":"save20","type":"percent","value":20,"maxDiscount":50}'

call 201 "add a flat 30 coupon" POST "/api/coupons" \
     '{"code":"FLAT30","type":"FLAT","value":30}'

call 201 "add an already-expired coupon, for the rejection case" POST "/api/coupons" \
     '{"code":"OLD10","type":"FLAT","value":10,"expiresAt":"2020-01-01T00:00:00Z"}'

call 400 "a percentage above 100 is rejected" POST "/api/coupons" \
     '{"code":"TOOMUCH","type":"PERCENT","value":120}'

call 400 "a duplicate code is rejected, case-insensitively" POST "/api/coupons" \
     '{"code":"Save20","type":"FLAT","value":5}'

call 200 "list coupons" GET "/api/coupons"

call 200 "estimate with a coupon — 20% off the 99.00 fare" \
     GET "/api/fare/estimate?distanceKm=16&carType=SEDAN&couponCode=save20"

call 400 "an expired coupon is reported, not silently ignored" \
     GET "/api/fare/estimate?distanceKm=16&carType=SEDAN&couponCode=OLD10"

# --- 5. the main flow --------------------------------------------------------------------------
section "5. Book a ride, end it, read the fare"

call 201 "book a sedan to Electronic City with a coupon (note the padding and case)" \
     POST "/api/rides" \
     "{\"userId\":\"$USER_A\",$PICKUP,$FAR_DROP,\"carType\":\"SEDAN\",\"radiusKm\":5,\"couponCode\":\"  save20  \"}"
RIDE=$(id_of)

call 409 "the same rider cannot hold two ongoing rides" POST "/api/rides" \
     "{\"userId\":\"$USER_A\",$PICKUP,$NEAR_DROP,\"carType\":\"SEDAN\"}"

call 200 "the ride shows as ongoing, with no fare yet" \
     GET "/api/users/$USER_A/rides?status=ONGOING"

call 200 "end the ride — itemised fare, discount applied after surge" \
     POST "/api/rides/$RIDE/end"

call 200 "the driver is back in the pool" GET "/api/drivers/$SEDAN"

call 409 "ending twice is refused rather than charging twice" POST "/api/rides/$RIDE/end"

call 404 "ending an unknown ride is a 404" POST "/api/rides/no-such-ride/end"

# --- 6. edge cases -----------------------------------------------------------------------------
section "6. Edge cases"

call 201 "hatchback requested, none free — a sedan takes it, upgraded:true" POST "/api/rides" \
     "{\"userId\":\"$USER_A\",$PICKUP,$FAR_DROP,\"carType\":\"HATCHBACK\",\"radiusKm\":5}"
UPGRADE_RIDE=$(id_of)

call 200 "the upgrade is free: billed on the HATCHBACK card, not the SEDAN one" \
     POST "/api/rides/$UPGRADE_RIDE/end"

call 201 "a ~250 m hop" POST "/api/rides" \
     "{\"userId\":\"$USER_A\",$PICKUP,$HOP_DROP,\"carType\":\"SEDAN\",\"radiusKm\":5}"
HOP_RIDE=$(id_of)

call 200 "minimum fare applies — 50.00 regardless of distance" POST "/api/rides/$HOP_RIDE/end"

call 409 "no driver within 100 m" POST "/api/rides" \
     "{\"userId\":\"$USER_A\",$PICKUP,$NEAR_DROP,\"carType\":\"SEDAN\",\"radiusKm\":0.1}"

call 400 "an unknown coupon is rejected at booking, before any driver is reserved" \
     POST "/api/rides" \
     "{\"userId\":\"$USER_A\",$PICKUP,$NEAR_DROP,\"carType\":\"SEDAN\",\"couponCode\":\"NOPE\"}"

call 200 "…and the driver was never taken out of the pool" GET "/api/drivers/$SEDAN"

call 404 "booking for an unknown rider is a 404" POST "/api/rides" \
     "{\"userId\":\"ghost\",$PICKUP,$NEAR_DROP,\"carType\":\"SEDAN\"}"

# --- 7. concurrency ----------------------------------------------------------------------------
section "7. Concurrency — two riders, one driver"

curl -s -X PATCH "$BASE/api/drivers/$HATCH/status" -H 'Content-Type: application/json' \
     -d '{"status":"OFFLINE"}' > /dev/null

RACE_OUT=$(mktemp -d)
for rider in "$USER_A" "$USER_B"; do
    curl -s -o "$RACE_OUT/$rider.body" -w '%{http_code}' -X POST "$BASE/api/rides" \
         -H 'Content-Type: application/json' \
         -d "{\"userId\":\"$rider\",$PICKUP,$NEAR_DROP,\"carType\":\"SEDAN\",\"radiusKm\":5}" \
         > "$RACE_OUT/$rider.status" &
done
wait

printf '\n%sboth riders request the last remaining driver simultaneously%s\n' "$BOLD" "$OFF"
CREATED=0
for rider in "$USER_A" "$USER_B"; do
    st=$(cat "$RACE_OUT/$rider.status")
    printf '  rider %s… → %s %s\n' "${rider:0:8}" "$st" "$(jq -c '.code // .status' "$RACE_OUT/$rider.body" 2>/dev/null)"
    [ "$st" = "201" ] && CREATED=$((CREATED + 1))
done

if [ "$CREATED" -eq 1 ]; then
    printf '  %s✓ exactly one booking won the driver%s\n' "$GREEN" "$OFF"
    PASS=$((PASS + 1))
else
    printf '  %s✗ %s bookings succeeded — expected exactly 1%s\n' "$RED" "$CREATED" "$OFF"
    FAIL=$((FAIL + 1))
fi
rm -rf "$RACE_OUT"

# --- 8. history --------------------------------------------------------------------------------
section "8. Ride history"

call 200 "rider history, all rides" GET "/api/users/$USER_A/rides"
call 200 "rider history, completed only" GET "/api/users/$USER_A/rides?status=COMPLETED"
call 200 "driver history" GET "/api/drivers/$SEDAN/rides"
call 400 "an unrecognised status filter is reported" GET "/api/users/$USER_A/rides?status=WEIRD"
call 404 "history for an unknown rider is a 404" GET "/api/users/ghost/rides"

# --- 9. coupon cleanup -------------------------------------------------------------------------
section "9. Delete coupons"

call 204 "delete SAVE20" DELETE "/api/coupons/SAVE20"
call 404 "deleting it again is a 404 — lookup normalises the code" DELETE "/api/coupons/save20"

# --- summary -----------------------------------------------------------------------------------
printf '\n%s══ Summary %s\n' "$BOLD$CYAN" "$OFF"
printf '  %s%s passed%s' "$GREEN" "$PASS" "$OFF"
if [ "$FAIL" -gt 0 ]; then
    printf ', %s%s failed%s\n' "$RED" "$FAIL" "$OFF"
    exit 1
fi
printf '\n'
