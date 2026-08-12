# API cURL Reference — phase by phase

Every command needed to exercise the service, grouped by the build phase that introduces it.
Status legend: **[live]** works now · **[phase N]** lands in that phase.

Base URL: `http://localhost:8080`

---

## Phase 0 — run the service **[live]**

```bash
# Option A: credentials from the git-ignored local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Option B: credentials from the environment
export MYSQL_PASSWORD='<your-mysql-password>'
./mvnw spring-boot:run
```

Health check:

```bash
curl -i http://localhost:8080/api/users -X POST \
  -H 'Content-Type: application/json' -d '{}'
# expect 400 VALIDATION_FAILED once controllers land — proves the app is up and the
# error contract is wired
```

Useful shell setup for everything below:

```bash
BASE=http://localhost:8080
# MG Road, Bengaluru — used as the pickup point throughout
PICKUP_LAT=12.9716;  PICKUP_LNG=77.5946
# Koramangala — roughly 7 km away, so the ride crosses all three pricing tiers
DROP_LAT=12.9279;    DROP_LNG=77.6271
```

---

## Phase 1 — schema and invariants **[live]**

No HTTP surface yet; this phase is entities plus repositories. Verify it at the database.

```bash
# tables exist
mysql -uroot -p -D plazza -e "SHOW TABLES;"

# the active-ride unique indexes that make 'one ongoing ride per rider' a DB guarantee
mysql -uroot -p -D plazza -e "SHOW INDEX FROM rides WHERE Key_name LIKE 'uk_%';"

# the geo columns the driver search prefilters on
mysql -uroot -p -D plazza -e "SHOW INDEX FROM drivers;"

# proof that NULLs are exempt from the unique index — the reason the invariant works
mysql -uroot -p -D plazza -e "
  CREATE TEMPORARY TABLE nulltest(id INT, active VARCHAR(10), UNIQUE KEY uk(active));
  INSERT INTO nulltest VALUES (1,NULL),(2,NULL),(3,'u1');
  INSERT INTO nulltest VALUES (4,'u1');"   # fails with 1062, as it should
```

---

## Phase 2 — pricing engine **[phase 2]**

The pricing tests need neither Spring nor MySQL, so they are the fastest proof the core is right:

```bash
./mvnw test -Dtest='TieredFareCalculatorTest,DiscountPolicyTest'
```

Fare quote without booking anything:

```bash
curl -s "$BASE/api/fare/estimate?distanceKm=7&carType=SEDAN" | jq
# 2 km x 10 + 3 km x 8 + 2 km x 5 = 54.00

curl -s "$BASE/api/fare/estimate?distanceKm=3&carType=SEDAN" | jq
# slabs give 28.00, minimum fare floors it to 50.00

curl -s "$BASE/api/fare/estimate?distanceKm=7&carType=HATCHBACK" | jq
# same trip on the cheaper rate card = 42.00

curl -s "$BASE/api/fare/estimate?distanceKm=7&carType=SEDAN&couponCode=SAVE20" | jq
# discount applied after surge, capped at the coupon's maxDiscount
```

---

## Phase 3 — register riders and drivers **[phase 3]**

### Register a rider

```bash
USER_ID=$(curl -s -X POST "$BASE/api/users" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Aryant","phone":"9876500001"}' | jq -r .id)
echo "user: $USER_ID"
```

### Register drivers

```bash
# sedan, ~0.5 km from pickup
SEDAN_ID=$(curl -s -X POST "$BASE/api/drivers" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ravi","carType":"SEDAN","rating":4.8,"lat":12.9750,"lng":77.5980}' | jq -r .id)

# hatchback, ~1.2 km from pickup
HATCH_ID=$(curl -s -X POST "$BASE/api/drivers" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Suresh","carType":"HATCHBACK","rating":4.2,"lat":12.9800,"lng":77.6020}' | jq -r .id)

# sedan far outside any sane radius (~19 km) — used for the no-driver edge case
FAR_ID=$(curl -s -X POST "$BASE/api/drivers" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Distant","carType":"SEDAN","rating":5.0,"lat":13.1400,"lng":77.7000}' | jq -r .id)

echo "sedan=$SEDAN_ID hatchback=$HATCH_ID far=$FAR_ID"
```

### Update a cab's location

```bash
curl -s -X PATCH "$BASE/api/drivers/$SEDAN_ID/location" \
  -H 'Content-Type: application/json' \
  -d '{"lat":12.9730,"lng":77.5960}' | jq
```

### Take a driver offline / bring them back

```bash
curl -s -X PATCH "$BASE/api/drivers/$HATCH_ID/status" \
  -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}' | jq

curl -s -X PATCH "$BASE/api/drivers/$HATCH_ID/status" \
  -H 'Content-Type: application/json' -d '{"status":"AVAILABLE"}' | jq
```

### Validation failures worth showing

```bash
# blank name -> 400 VALIDATION_FAILED with per-field messages
curl -s -X POST "$BASE/api/users" \
  -H 'Content-Type: application/json' -d '{"name":"   ","phone":""}' | jq

# latitude out of range -> 400
curl -s -X POST "$BASE/api/drivers" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bad","carType":"SEDAN","rating":4.0,"lat":99.9,"lng":77.6}' | jq
```

---

## Phase 4 — book and end a ride **[phase 4]**

### Book

```bash
RIDE_ID=$(curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"SEDAN\",
       \"radiusKm\":5}" | jq -r .id)
echo "ride: $RIDE_ID"
```

### End the ride and read the itemised fare

```bash
curl -s -X POST "$BASE/api/rides/$RIDE_ID/end" | jq
# {
#   "distanceKm": 6.9,
#   "billedCarType": "SEDAN",
#   "baseFare": 53.50,
#   "surgeMultiplier": 1.00,
#   "fareAfterSurge": 53.50,
#   "discount": 0.00,
#   "total": 53.50
# }
```

### Edge case — no driver inside the radius

```bash
curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"SEDAN\",
       \"radiusKm\":0.1}" | jq
# 409 {"code":"NO_DRIVER_AVAILABLE", ...}
```

### Edge case — free hatchback to sedan upgrade

```bash
# take the only hatchback offline so none is reachable
curl -s -X PATCH "$BASE/api/drivers/$HATCH_ID/status" \
  -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}' > /dev/null

curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"HATCHBACK\",
       \"radiusKm\":5}" | jq
# "assignedCarType":"SEDAN", "requestedCarType":"HATCHBACK", "upgraded":true
# ending it bills the HATCHBACK rate card: 42.00, not 54.00
```

### Edge case — minimum fare

```bash
# a very short hop: slab total lands under the floor, so the minimum fare wins
curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":12.9740,\"lng\":77.5970},
       \"carType\":\"SEDAN\",\"radiusKm\":5}" | jq
```

### Edge case — a rider cannot hold two ongoing rides

```bash
# with one ride already ONGOING, booking again returns 409 DUPLICATE_ACTIVE_RIDE,
# rejected by the uk_active_user index rather than by an application-level check
```

---

## Phase 5 — coupons and history **[phase 5]**

### Add coupons

```bash
curl -s -X POST "$BASE/api/coupons" \
  -H 'Content-Type: application/json' \
  -d '{"code":"SAVE20","type":"PERCENT","value":20,"maxDiscount":50}' | jq

curl -s -X POST "$BASE/api/coupons" \
  -H 'Content-Type: application/json' \
  -d '{"code":"FLAT30","type":"FLAT","value":30}' | jq

# expired on purpose, for the rejection demo
curl -s -X POST "$BASE/api/coupons" \
  -H 'Content-Type: application/json' \
  -d '{"code":"OLD10","type":"FLAT","value":10,"expiresAt":"2020-01-01T00:00:00Z"}' | jq
```

### Book with a coupon

```bash
curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"SEDAN\",\"radiusKm\":5,
       \"couponCode\":\"  save20  \"}" | jq
# note the padding and lower case: codes normalise through StringUtils, so this is SAVE20
```

### Coupon rejections

```bash
# unknown code -> 400 INVALID_COUPON
curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"SEDAN\",\"couponCode\":\"NOPE\"}" | jq

# expired code -> 400 INVALID_COUPON
curl -s -X POST "$BASE/api/rides" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"SEDAN\",\"couponCode\":\"OLD10\"}" | jq
```

### Delete a coupon

```bash
curl -i -s -X DELETE "$BASE/api/coupons/SAVE20"      # 204
curl -i -s -X DELETE "$BASE/api/coupons/save20"      # 404 — already gone, lookup normalises
```

### Ride history

```bash
curl -s "$BASE/api/users/$USER_ID/rides" | jq                      # all
curl -s "$BASE/api/users/$USER_ID/rides?status=ONGOING" | jq
curl -s "$BASE/api/users/$USER_ID/rides?status=COMPLETED" | jq

curl -s "$BASE/api/drivers/$SEDAN_ID/rides" | jq
curl -s "$BASE/api/drivers/$SEDAN_ID/rides?status=COMPLETED" | jq
```

---

## Phase 6 — bonus features **[phase 6]**

### Cancel a ride

```bash
curl -s -X POST "$BASE/api/rides/$RIDE_ID/cancel" | jq
# returns the cancellation fee from the configured policy and frees the driver

# cancelling twice -> 409 ILLEGAL_RIDE_STATE
curl -s -X POST "$BASE/api/rides/$RIDE_ID/cancel" | jq
```

### Swap the matching strategy — configuration only, no code change

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--matching.strategy=highestRated
# bookings now prefer the best-rated driver in radius instead of the closest one
```

### Concurrency: two riders racing for the last driver

```bash
# leave exactly one driver available, then fire two bookings at once.
# expect one 201 and one 409 NO_DRIVER_AVAILABLE — never two rides on one driver.
curl -s -X POST "$BASE/api/rides" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_A\",\"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},\"carType\":\"SEDAN\"}" &
curl -s -X POST "$BASE/api/rides" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_B\",\"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},\"carType\":\"SEDAN\"}" &
wait

# confirm at the database: exactly one ongoing ride on that driver
mysql -uroot -p -D plazza -e \
  "SELECT id, driver_id, status FROM rides WHERE status='ONGOING';"
```

### Surge

```bash
curl -s "$BASE/api/fare/estimate?distanceKm=7&carType=SEDAN" | jq .surgeMultiplier
# 1.00 with the default NoSurgeStrategy; the demand/supply implementation raises it
```

---

## Demo run sheet — one paste, start to finish

```bash
BASE=http://localhost:8080
PICKUP_LAT=12.9716; PICKUP_LNG=77.5946
DROP_LAT=12.9279;   DROP_LNG=77.6271

USER_ID=$(curl -s -X POST "$BASE/api/users" -H 'Content-Type: application/json' \
  -d '{"name":"Aryant","phone":"9876500001"}' | jq -r .id)

SEDAN_ID=$(curl -s -X POST "$BASE/api/drivers" -H 'Content-Type: application/json' \
  -d '{"name":"Ravi","carType":"SEDAN","rating":4.8,"lat":12.9750,"lng":77.5980}' | jq -r .id)

curl -s -X POST "$BASE/api/coupons" -H 'Content-Type: application/json' \
  -d '{"code":"SAVE20","type":"PERCENT","value":20,"maxDiscount":50}' > /dev/null

curl -s -X PATCH "$BASE/api/drivers/$SEDAN_ID/location" -H 'Content-Type: application/json' \
  -d '{"lat":12.9730,"lng":77.5960}' | jq

RIDE_ID=$(curl -s -X POST "$BASE/api/rides" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",
       \"pickup\":{\"lat\":$PICKUP_LAT,\"lng\":$PICKUP_LNG},
       \"drop\":{\"lat\":$DROP_LAT,\"lng\":$DROP_LNG},
       \"carType\":\"SEDAN\",\"radiusKm\":5,\"couponCode\":\"save20\"}" | jq -r .id)

curl -s "$BASE/api/users/$USER_ID/rides?status=ONGOING" | jq
curl -s -X POST "$BASE/api/rides/$RIDE_ID/end" | jq
curl -s "$BASE/api/users/$USER_ID/rides?status=COMPLETED" | jq
curl -s "$BASE/api/drivers/$SEDAN_ID/rides" | jq
```

---

## Error responses

Every failure returns the same shape:

```json
{
  "code": "NO_DRIVER_AVAILABLE",
  "message": "no driver available within 5.0 km",
  "timestamp": "2026-08-12T10:22:31.004Z"
}
```

| HTTP | `code` | Cause |
|---|---|---|
| 400 | `VALIDATION_FAILED` | bean validation or a domain input check; carries `fieldErrors` |
| 400 | `INVALID_COUPON` | unknown, inactive, or expired coupon |
| 404 | `USER_NOT_FOUND` / `DRIVER_NOT_FOUND` / `RIDE_NOT_FOUND` | unknown id |
| 409 | `NO_DRIVER_AVAILABLE` | nobody in radius, or every candidate lost its reservation race |
| 409 | `ILLEGAL_RIDE_STATE` | ending or cancelling a ride that is already terminal |
| 409 | `DUPLICATE_ACTIVE_RIDE` | the `uk_active_user` / `uk_active_driver` index rejected a second ongoing ride |
