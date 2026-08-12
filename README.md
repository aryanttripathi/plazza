# Ride Hailing Service — Backend

A ride hailing backend: register riders and drivers, book a ride against the nearest available
driver, price it through a configurable tiered fare engine, and end it with an itemised bill.

**Stack:** Java 17 · Spring Boot 4.1 · Spring Data JPA · MySQL 8 · Apache Commons Lang3 · JUnit 5 + AssertJ

---

## Quick start

```bash
# 1. MySQL must be running locally. The schema is created on first boot.
#    Supply credentials one of two ways:
export MYSQL_PASSWORD='your-password'          # or put them in application-local.yml (git-ignored)

# 2. Run
./mvnw spring-boot:run

# 3. Open the demo console
open http://localhost:8080/

# 4. Or drive the whole flow from the shell — 49 checks, prints every request and response
./scripts/e2e.sh
```

**Tests**

```bash
./mvnw test      # 67 unit tests — no Spring, no database, runs in under a second
./mvnw verify    # the above plus 43 integration tests against real MySQL
```

The two tiers are deliberately separate. The pricing engine — the part most worth being sure about
— has no Spring context and no database, so it stays fast and can never fail for an environmental
reason. The integration tier exists for the claims that only a real database can settle: atomic
driver reservation, the one-active-ride-per-rider constraint, and the geo query.

---

## What's implemented

| # | Requirement | Status |
|---|---|---|
| 1 | Register a user | done |
| 2 | Register a driver | done |
| 3 | Book a ride if a driver is available within a radius | done |
| 4 | End a ride and get the cost | done |
| 5 | Update the location of a cab | done |
| 6 | Tiered pricing strategy | done — rate cards are configuration, not code |
| 7 | Ride history for a user (ongoing + completed) | done |
| 8 | Ride history for a driver | done |
| 9 | Sedan/Hatchback with different rates, free upgrade | done |
| 10 | Coupons: add, delete, apply | done |
| B1 | Surge pricing (pluggable multiplier) | interface + inert default shipped; no demand-based implementation |
| B2 | Configurable driver-matching strategy | done — `matching.strategy: nearest \| highestRated` |
| B3 | Ride cancellation with a fee policy | **not implemented** |
| B4 | Concurrency safety on double booking | done, with tests |

---

## Assumptions

1. **Slab continuity.** The brief says "first 2 km @₹10, 3–5 km @₹8, 6 km+ @₹5", which leaves gaps
   at 2–3 km and 5–6 km. I assumed contiguous half-open slabs: `[0,2)@₹10`, `[2,5)@₹8`, `[5,∞)@₹5`.
   The registry rejects a card with a gap or overlap at startup.
2. **Minimum fare is a floor on the base fare**, applied before surge and before any coupon.
   A 3 km sedan trip costs ₹50, not its ₹28 of slabs — and a 1.5× surge then applies to ₹50.
3. **Pipeline order:** slabs → floor at minimum fare → × surge → − discount → floor at zero → round
   HALF_UP to 2dp. Changing this order changes the money for the same trip, so it lives in one place.
4. **Free upgrade means billing follows the requested car type.** Ask for a hatchback, get a sedan
   when no hatchback is free, pay the hatchback rate.
5. **Upgrade classes are tried cheapest-first**, one rank at a time. See the trade-offs below.
6. **Distance is straight-line haversine** between pickup and drop, not routed road distance.
7. **A coupon is validated at booking and applied at ride end**, when the fare exists.
8. **Ratings are static.** Nothing recomputes a driver's rating after a ride.
9. **Default search radius is 5 km**, overridable per booking request.
10. **One active ride per rider and per driver**, enforced by a database constraint.
11. **Money is `BigDecimal` in Java and `DECIMAL(10,2)` in MySQL.** No `double` in the fare path.
12. **No authentication, authorisation, rate limiting or pagination.**

---

## Architecture

```
Controller (validated DTOs)
    → Service (orchestration, transactions)
        → Strategy (pricing · matching · discount · surge)
        → Spring Data JPA repository
            → MySQL
```

Packaged **by feature**, not by layer:

```
common/     geo, money, text, errors — depends on nothing
user/       driver/     ride/     coupon/     matching/     pricing/
```

Each module publishes one service interface plus immutable view records in its root package;
entities, repositories and implementations live in `<module>/internal/` and are never imported
across a boundary. Cross-module calls carry views, never managed JPA entities. Nothing imports
`ride`, which is the orchestrator.

`RideService` contains no arithmetic. Every number it returns came from `pricing`, every driver
choice from `matching`, every discount from `coupon`. That is what makes each of them swappable
without touching booking logic.

Full detail, with diagrams: [docs/HLD.md](docs/HLD.md) · [docs/LLD.md](docs/LLD.md) ·
[docs/PLAN.md](docs/PLAN.md) · [docs/API_CURLS.md](docs/API_CURLS.md)

---

## Key design decisions and trade-offs

**Pricing is data, not code.** Rate cards live in `application.yml`. Adding an SUV is an enum
constant plus a config block; adding a tier is one row. There is no `if (carType == SEDAN)` anywhere.
`RateCardRegistry` validates every card at startup, because a gap between slabs does not crash —
it silently misprices, and the only symptom is money.

**The fare calculator takes a `DiscountResolver` callback, not a discount amount.** A discount
applies to the *post-surge* fare, which only the calculator knows. Passing a pre-computed number
would force callers to reproduce the pipeline order, putting fare logic back into `RideService`.
Pricing owns *when*, coupon owns *how much*, and the calculator clamps whatever comes back into
`[0, fare]` so one badly written policy cannot issue a refund.

**Matching returns an ordered list, not a single driver.** Returning one driver would make a lost
reservation race fatal: that driver gets taken and the request fails while others sit idle. The
booking loop walks the ranking until a reservation sticks, so concurrency stays inside `RideService`
and strategies remain pure ordering functions.

**Driver reservation is a conditional `UPDATE`, not a read-then-write.**

```sql
UPDATE drivers SET status = 'ON_TRIP' WHERE id = ? AND status = 'AVAILABLE'
```

Rows affected is the answer: 1 means you won, 0 means try the next candidate. One round trip, no
lock held across the match, correct across multiple app instances. `SELECT` then `save()` would be a
race; a `synchronized` block would serialise every booking in the system and still break under two
instances.

**One active ride per rider is a database constraint, not a service check.** `rides.active_user_id`
mirrors `user_id` while the ride is ongoing and is `NULL` once it ends. MySQL unique indexes ignore
`NULL`s, so `uk_active_user` enforces the rule for exactly as long as it should. Check-then-insert in
service code is a race that shows up as duplicate rides under load.

**Upgrade classes are tried cheapest-first, one rank at a time.** A single query across all upgrade
classes ranked by distance would dispatch an SUV parked 20 m away while a sedan waited 500 m off —
giving away the most expensive car in the fleet at the cheapest fare. Test:
`cheapestUpgradeClassWins`.

**Geo search is a bounding box then an exact filter.** The `BETWEEN` clauses on indexed `lat`/`lng`
cut the candidate set cheaply; `ST_Distance_Sphere` then applies the true circular test to the
survivors. Both derive from the same earth radius, so the prefilter can never drop a driver that is
genuinely in range. Verified: a driver 2.2 km north is found at radius 5 and not at radius 2.

**No H2 in tests.** The geo query is MySQL-specific and the active-ride trick depends on MySQL's
treatment of `NULL` in unique indexes. An H2 dialect that quietly accepted different SQL would
produce a false green on precisely the claims worth verifying.

**Ends and cancels take a row lock.** `endRide` loads the ride `FOR UPDATE`, so two concurrent end
requests serialise and the second sees `COMPLETED` rather than pricing the ride twice. Ride update
and driver release share one transaction: a ride can never complete while its driver stays `ON_TRIP`.

**A coupon that disappears mid-ride charges full fare rather than failing.** Refusing would leave a
rider unable to end their ride at all. It logs a warning and the breakdown shows a zero discount.
This is the weakest decision in the codebase — see below.

---

## What I would do differently with more time

- **Snapshot coupon terms onto the ride at booking.** Today the coupon is looked up again at ride
  end; if it was deleted or expired in between, the rider silently loses a discount they were
  promised. Copying type, value and cap onto the ride row at booking makes the agreed price immune
  to later edits. This is the first thing I would fix.
- **Flyway migrations with `ddl-auto: validate`.** Deriving the schema from entities is fine for an
  exercise and wrong for anything that has to be upgraded in place.
- **Ride cancellation with a fee policy (B3).** The `CancellationPolicy` seam is designed but not
  built; it was the item I cut when the clock ran down.
- **A real surge strategy.** `SurgeStrategy` is pluggable and ships inert. A useful implementation
  needs a rolling demand-versus-supply window per geo cell, plus a cap.
- **Spatial index for driver search.** The bounding box is index-assisted but still a scan within
  the box. A geohash bucket or a MySQL `SPATIAL INDEX` on a `POINT` column replaces it inside one
  repository method.
- **Idempotency keys on booking.** A retried `POST /api/rides` can create a second ride once the
  first has ended.
- **Pagination on ride history**, and reads served from a replica.
- **A ride lifecycle event log** for fare audit and analytics.
- **Enforce module boundaries mechanically** — ArchUnit, or a real multi-module Maven build. Today
  `<module>/internal` is a convention held up by review rather than by the compiler.
- **Rating updates after rides**, so `highestRated` matching operates on something that moves.

---

## How I used AI

I used Claude (in Claude Code) throughout, and reviewed everything it produced. What that looked
like in practice:

**What I prompted for.** The plan and the HLD/LLD documents first, so the module boundaries and the
pricing pipeline order were settled before any code existed. Then each phase in turn: entities and
repositories, the pricing engine tests-first, matching, booking, coupons. Boilerplate — DTOs, view
records, mapping functions, the JavaDoc — is almost entirely generated. So is `scripts/e2e.sh`.

**What I rejected or rewrote, and why.**

- The LLD specified `FareCalculator.calculate(..., BigDecimal discount)`. Writing the code exposed a
  chicken-and-egg problem: the discount depends on the post-surge fare, which only the calculator
  computes, so the caller would have had to know the pipeline order. I replaced it with the
  `DiscountResolver` callback and updated the design doc to match.
- `NoSurgeStrategy` was first written as `@Component @ConditionalOnMissingBean`. That annotation is
  only reliable on `@Bean` methods; on a component class it depends on bean-definition ordering.
  Moved to a `@Bean` method in `PricingConfig`.
- `MatchingStrategyResolver` built a case-insensitive `TreeMap` and then copied it into a
  `LinkedHashMap`, which silently discards the comparator — `matching.strategy: HIGHESTRATED` would
  have failed at startup. Caught in review, fixed, and pinned with a test.
- Two of my first pricing tests asserted slab totals that the ₹50 minimum fare swallows, so they
  passed for the wrong reason. Rather than patch the expected numbers I moved those assertions onto
  a zero-minimum rate card, so they test slabs instead of re-testing the floor.
- `mvn test` matches `*Test` only, so every `*IT` class was being skipped and a green run proved
  nothing about the database. Added the Failsafe plugin.
- A boot failure reported `Unable to determine Dialect without JDBC metadata`. The real cause was
  `Access denied` two lines earlier. I set the dialect explicitly so the actual error surfaces first.
- I asked for and then cut a fare-estimate endpoint inside the `pricing` module: it needs coupons,
  and `pricing` must not depend on `coupon`. It lives in `ride`, which already depends on both.

**Where AI was least useful.** Anything requiring a decision about a trade-off. Every choice in the
section above — the conditional `UPDATE`, the nullable unique column, cheapest-first upgrades, no H2
— came from thinking about failure modes, and the generated first drafts of each were plausible and
wrong in ways only visible if you already knew what you were looking for.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/users` | Register a rider |
| `GET` | `/api/users/{id}/rides?status=` | Rider history |
| `POST` | `/api/drivers` | Register a driver |
| `GET` | `/api/drivers/{id}` | Driver detail |
| `PATCH` | `/api/drivers/{id}/location` | Update cab location |
| `PATCH` | `/api/drivers/{id}/status` | Go online / offline |
| `GET` | `/api/drivers/{id}/rides?status=` | Driver history |
| `POST` | `/api/rides` | Book a ride |
| `POST` | `/api/rides/{id}/end` | End a ride, returns the fare breakdown |
| `GET` | `/api/rides/{id}` | Ride detail |
| `POST` | `/api/coupons` | Add a coupon |
| `GET` | `/api/coupons` | List coupons |
| `DELETE` | `/api/coupons/{code}` | Delete a coupon |
| `GET` | `/api/fare/estimate?distanceKm=&carType=&couponCode=` | Quote without booking |

Every failure returns the same shape:

```json
{ "code": "NO_DRIVER_AVAILABLE", "message": "no driver available within 5.0 km", "timestamp": "…" }
```

| HTTP | Code | Cause |
|---|---|---|
| 400 | `VALIDATION_FAILED` | bean validation or a domain check; includes `fieldErrors` |
| 400 | `INVALID_COUPON` | unknown, inactive or expired coupon |
| 404 | `USER_NOT_FOUND` · `DRIVER_NOT_FOUND` · `RIDE_NOT_FOUND` · `COUPON_NOT_FOUND` | unknown id |
| 409 | `NO_DRIVER_AVAILABLE` | nobody in radius, or every candidate lost its reservation race |
| 409 | `ILLEGAL_RIDE_STATE` | ending a ride that is already terminal |
| 409 | `DUPLICATE_ACTIVE_RIDE` | the rider or driver already has an ongoing ride |

---

## Configuration

```yaml
pricing:
  cards:
    SEDAN:
      minimum-fare: 50
      tiers:
        - { from-km: 0, to-km: 2,      rate-per-km: 10 }
        - { from-km: 2, to-km: 5,      rate-per-km: 8 }
        - { from-km: 5, to-km: 100000, rate-per-km: 5 }
matching:
  strategy: nearest        # or highestRated
booking:
  default-radius-km: 5
```

Credentials come from `MYSQL_USER` / `MYSQL_PASSWORD`, or from `application-local.yml`, which is
git-ignored. The app defaults to the `local` profile so it runs from an IDE with no extra setup.

**Live extension cheat sheet**

| Change | Where |
|---|---|
| Add an SUV car type | `CarType` constant + a rate-card block in `application.yml` |
| Add a pricing tier | one row in `application.yml` |
| Add a coupon kind | one `DiscountPolicy` implementation |
| Switch matching rule | `matching.strategy` |
| Bill the upgraded car at its own rate | one line in `RideServiceImpl` |
