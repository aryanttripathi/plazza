# Ride Hailing Backend — Execution Plan

**Time budget:** 2 hours.
**Stack:** Java 17 · Spring Boot 4.1.0 (REST) · **Spring Data JPA + MySQL 8** · Apache Commons Lang3 · Lombok · JUnit 5.
**Code shape:** package-by-feature modules with enforced boundaries (see [LLD §1](./LLD.md#1-modular-package-layout)).

---

## 1. Scope

### Mandatory (must ship)
| # | Requirement | Where it lands |
|---|---|---|
| M1 | Register a user | `user` module — `UserService`, `POST /api/users` |
| M2 | Register a driver | `driver` module — `DriverService`, `POST /api/drivers` |
| M3 | Book ride if driver available within radius | `ride` module — `RideService` + `matching` module |
| M4 | End ride, return cost | `RideService.endRide` + `pricing` module |
| M5 | Update cab location | `PATCH /api/drivers/{id}/location` |
| M6 | Tiered pricing | `TieredFareCalculator` + `RateCard` config |
| M7 | User ride history (ongoing + completed) | `GET /api/users/{id}/rides` |
| M8 | Driver ride history | `GET /api/drivers/{id}/rides` |
| M9 | Car types (Sedan/Hatchback) + free upgrade | `CarType` + `requestedCarType` vs `assignedCarType` |
| M10 | Coupons: add / delete / apply | `coupon` module + `DiscountPolicy` |

### Bonus (only after mandatory is green)
| # | Requirement | Where it lands |
|---|---|---|
| B1 | Surge pricing (pluggable multiplier) | `pricing.surge.SurgeStrategy`, default `NoSurgeStrategy` |
| B2 | Configurable matching strategy | `matching.strategy` property → strategy bean resolved by name |
| B3 | Ride cancellation + fee policy | `RideService.cancel` + `CancellationPolicy` |
| B4 | Concurrency safety | **Conditional UPDATE in MySQL** (DB-level CAS), see [LLD §6](./LLD.md#6-concurrency-two-riders-one-driver) |

---

## 2. Assumptions (state these at demo start)

1. **Slab continuity.** Spec says "first 2 km @₹10, 3–5 km @₹8, 6 km+ @₹5" — gaps at 2–3 km and 5–6 km. Assumed contiguous half-open slabs: `[0,2)@₹10`, `[2,5)@₹8`, `[5,∞)@₹5`. Configurable.
2. **Minimum fare is a floor on the base fare**, applied before surge and before coupon: `base = max(minFare, slabSum)`.
3. **Pricing order:** `slabSum → floor at minFare → × surge → − coupon discount → floor at 0 → round HALF_UP 2dp`.
4. **Free upgrade = billing follows the requested car type.** Request Hatchback, none in radius, Sedan available → Sedan assigned, Hatchback rate card billed.
5. **Distance** = haversine (straight line) between pickup and drop. Real routed distance is a `DistanceCalculator` swap.
6. **Coupon attached at booking, evaluated at ride end** when the fare is known; validity re-checked at end.
7. **Persistence is MySQL 8** on localhost, schema `plazza`, created by Hibernate `ddl-auto=update` for the exercise. Flyway is the production answer (see §8).
8. **No auth, no rate limiting.**
9. **Default search radius 5 km**, overridable per booking request.
10. **One active ride per user and per driver** — enforced by a DB unique constraint, not only by service code.
11. **Money is `DECIMAL(10,2)` in MySQL and `BigDecimal` in Java.** No `double` anywhere in the fare path.
12. **All string handling goes through `org.apache.commons.lang3.StringUtils`** — no hand-rolled `s == null || s.trim().isEmpty()`.

---

## 3. Architecture in one line

`Controller (DTO) → Service (orchestration, @Transactional) → Strategy (pricing / matching / discount / surge) → Spring Data JPA repository → MySQL`

Every axis the interviewer can ask to change live is either a **strategy bean** or a **config entry**:

| Live change | Cost |
|---|---|
| New car type (SUV) | one enum constant + one rate-card block in yaml |
| New pricing tier | one yaml row |
| New coupon type | one `DiscountPolicy` implementation |
| Swap nearest ↔ highest-rated matching | one property value |

Details: [HLD.md](./HLD.md) · [LLD.md](./LLD.md) · runnable requests per phase: [API_CURLS.md](./API_CURLS.md).

---

## 4. Modularity rules (non-negotiable during implementation)

1. **Package by feature, not by layer.** `user`, `driver`, `ride`, `pricing`, `coupon`, `matching`, `common`.
2. **Each module exposes exactly one public service interface** in its root package. Entities, repositories, and internal helpers live under `<module>/internal/` and are never imported across module boundaries.
3. **Cross-module data passes as DTOs / value objects**, never as JPA entities — `RideService` never holds a managed `Driver` entity from the driver module.
4. **The `ride` module orchestrates and owns no arithmetic.** Every number it returns came from `pricing`, `matching`, or `coupon`.
5. **`common` depends on nothing.** Everything may depend on `common`.

This is a single Maven module with enforced package boundaries rather than a multi-module reactor build — the boundary discipline is identical and the 2-hour budget does not survive a reactor split. Promoting each package to its own Maven module later is mechanical.

---

## 5. Timeboxed schedule

| Slot | Work | Exit criteria |
|---|---|---|
| **0:00–0:10** | Docs, pom (JPA, MySQL, validation, commons-lang3), `application.yml`, MySQL schema reachable | App boots, connects to MySQL |
| **0:10–0:35** | `common` + entities + Spring Data repositories + `GeoUtils` | Tables created, repositories inject |
| **0:35–1:00** | **`pricing` module + its tests first** (tiers, min fare, car-type rates, coupon, upgrade billing) | Pricing tests green — this is the graded core, and it needs no DB |
| **1:00–1:25** | `matching` module, booking flow, end-ride flow, controllers | Book → end → fare works end to end against MySQL |
| **1:25–1:40** | Coupon CRUD, ride history endpoints, `GlobalExceptionHandler` | All mandatory endpoints live |
| **1:40–1:52** | Bonus: surge hook, cancellation, conditional-update concurrency + its test | Bonus green |
| **1:52–2:00** | README (assumptions, trade-offs, AI notes), full `mvn test`, final commit | Clean build, incremental commits |

**Cut order if short on time:** B1 surge → B3 cancellation → history status filters. **Never cut pricing tests.**

---

## 6. Test strategy under a real database

Two tiers, deliberately separated so a DB outage cannot block the graded core:

| Tier | Runs against | Contents | Speed |
|---|---|---|---|
| **Unit** | no Spring, no DB | `TieredFareCalculatorTest`, `DiscountPolicyTest`, `MatchingStrategyTest`, `GeoUtilsTest`, `RideServiceTest` (mocked ports) | milliseconds |
| **Integration** | local MySQL, schema `plazza_test` | `@DataJpaTest` repository tests (conditional-update CAS, unique active-ride constraint), `@SpringBootTest` book→end happy path | seconds |

No H2 substitute: the driver search uses MySQL-specific geo SQL, and an H2 dialect that silently accepts different SQL would be a false green. Integration tests point at the local MySQL via the `test` profile.

| Test | Asserts |
|---|---|
| `sedan_3km_hitsMinimumFare` | ₹28 slab sum → floored to ₹50 |
| `sedan_7km_tieredSlabs` | 2×10 + 3×8 + 2×5 = ₹54 |
| `sedan_2km_exactBoundary` | half-open slab, no double count |
| `hatchback_cheaperThanSedan` | car-type rate card applied |
| `hatchbackRequest_sedanAssigned_billedAsHatchback` | free-upgrade case |
| `percentCoupon_cappedAtMaxDiscount` | discount cap honoured |
| `flatCoupon_neverDrivesFareNegative` | total floors at 0 |
| `expiredCoupon_rejected` | `InvalidCouponException` |
| `surgeAppliedAfterMinimumFare` | pipeline ordering |
| `noDriverInRadius_throwsNoDriverAvailable` | booking edge case |
| `concurrentBookings_sameDriver_onlyOneWins` | 2 threads, 1 driver → exactly one ride (integration, real MySQL) |
| `secondActiveRideForUser_rejected` | DB unique constraint fires |

---

## 7. Demo script (30 min slot)

1. **8 min live run** — register user + drivers → book → update location → end ride → itemised fare. Edge cases: no driver in radius, minimum-fare ride, hatchback→sedan upgrade, invalid coupon. Show the rows in MySQL.
2. **8 min walkthrough** — module boundaries, where each strategy lives, why pricing is a config-driven rate card and not `if/else`, why driver reservation is a conditional UPDATE.
3. **10 min live extension** — SUV = enum + yaml; flat coupon = one `DiscountPolicy`; new tier = one yaml row. Show the proving test.
4. **4 min Q&A.**

---

## 8. Deliverable checklist

- [ ] Working API on MySQL, all 10 mandatory requirements
- [ ] Unit tests covering the full pricing engine + integration tests for the concurrency and constraint claims
- [ ] README: assumptions, design decisions + trade-offs, what I'd do with more time (Flyway migrations, geohash index, idempotency keys, event log), how AI was used — what was prompted, what was rejected or rewritten, and why
- [ ] Incremental commits with meaningful messages
- [ ] No dead code, no unexplained AI output, no unused dependency in the pom
