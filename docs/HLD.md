# High Level Design — Ride Hailing Backend

## 1. System context

Single Spring Boot service, REST over HTTP, **MySQL 8 for persistence**. State lives in the database, not in process memory, so the concurrency and uniqueness guarantees are enforced by the storage engine rather than by hopeful service code.

```mermaid
flowchart LR
    RC["Rider client<br/>(curl / Postman)"] --> API
    DC["Driver client<br/>(location pings)"] --> API
    AD["Admin<br/>(coupon CRUD)"] --> API

    subgraph SVC["Ride Hailing Service (Spring Boot 4.1)"]
        API["REST API layer<br/>controllers + DTO validation"] --> APP["Feature modules<br/>user · driver · ride · coupon"]
        APP --> STRAT["Strategy layer<br/>pricing · matching · discount · surge"]
        APP --> REPO["Spring Data JPA repositories"]
    end

    REPO --> DB[("MySQL 8<br/>schema: plazza")]

    style DB fill:#fde7e9,stroke:#d93025
```

---

## 2. Modular structure

Package **by feature**, not by layer. Each module owns its entities, repositories, and services, and publishes exactly one service interface. Internals sit under `<module>/internal/` and are never imported across a boundary.

```mermaid
flowchart TD
    subgraph API["api edge (thin controllers, per module)"]
        direction LR
        UC[UserController] --- DCtl[DriverController] --- RCtl[RideController] --- CCtl[CouponController]
    end

    RIDE["ride module<br/><b>orchestrator</b><br/>RideService"]
    USER["user module<br/>UserService"]
    DRIVER["driver module<br/>DriverService<br/>reservation + geo search"]
    PRICING["pricing module<br/>FareCalculator · RateCard · SurgeStrategy"]
    COUPON["coupon module<br/>CouponService · DiscountPolicy"]
    MATCHING["matching module<br/>DriverMatchingStrategy"]
    COMMON["common<br/>GeoUtils · errors · money · Strings"]
    DB[("MySQL")]

    API --> RIDE
    API --> USER
    API --> DRIVER
    API --> COUPON

    RIDE --> USER
    RIDE --> DRIVER
    RIDE --> PRICING
    RIDE --> COUPON
    RIDE --> MATCHING
    MATCHING --> DRIVER

    USER --> COMMON
    DRIVER --> COMMON
    PRICING --> COMMON
    COUPON --> COMMON
    RIDE --> COMMON

    USER --> DB
    DRIVER --> DB
    RIDE --> DB
    COUPON --> DB

    style RIDE fill:#e8f0fe,stroke:#4285f4
    style COMMON fill:#e6f4ea,stroke:#34a853
    style DB fill:#fde7e9,stroke:#d93025
```

**Dependency rules**

1. Arrows point one way. No module imports the `ride` module.
2. `common` depends on nothing; everything may depend on `common`.
3. Cross-module calls carry DTOs / value objects, never managed JPA entities.
4. `ride` orchestrates and contains no arithmetic — every number it returns came from `pricing`, `matching`, or `coupon`.

Why it matters for the demo: the live extension exercise ("add an SUV", "add a tier", "add a coupon type") lands inside exactly one module or in configuration, and nothing above it recompiles.

---

## 3. Core flow — book a ride

```mermaid
sequenceDiagram
    autonumber
    actor U as Rider
    participant C as RideController
    participant RS as RideService @Transactional
    participant CS as CouponService
    participant DS as DriverService
    participant MS as MatchingStrategy
    participant DB as MySQL

    U->>C: POST /api/rides {userId, pickup, drop, carType, radiusKm, couponCode?}
    C->>C: @Valid DTO, StringUtils.trimToNull on codes
    C->>RS: book(BookRideCommand)
    RS->>DB: user exists? no ONGOING ride for user?
    RS->>CS: validateIfPresent(couponCode)

    RS->>DS: findAvailableWithin(pickup, radiusKm, requestedCarType)
    DS->>DB: bounding-box prefilter (indexed lat/lng)<br/>+ ST_Distance_Sphere exact filter
    DB-->>DS: candidate drivers

    alt requested car type available
        DS-->>RS: exact-type candidates (upgraded = false)
    else requested type unavailable
        DS-->>RS: higher-tier candidates (upgraded = true)
        Note over RS: assignedCarType = SEDAN<br/>billedCarType = HATCHBACK — free upgrade
    end

    RS->>MS: rank(candidates, pickup)
    MS-->>RS: ordered list

    loop first candidate whose conditional UPDATE affects 1 row
        RS->>DB: UPDATE drivers SET status='ON_TRIP'<br/>WHERE id=? AND status='AVAILABLE'
    end

    alt no candidate reserved
        RS-->>C: NoDriverAvailableException
        C-->>U: 409 NO_DRIVER_AVAILABLE
    else reserved
        RS->>DB: INSERT ride (status=ONGOING, active_user_id=userId)
        RS-->>C: RideResponse
        C-->>U: 201 {rideId, driver, assignedCarType, upgraded:true}
    end
```

`rank(...)` returns an **ordered list**, not a single winner. A lost reservation race therefore falls through to the next driver instead of failing the booking.

---

## 4. Core flow — end a ride and price it

```mermaid
sequenceDiagram
    autonumber
    actor U as Rider
    participant C as RideController
    participant RS as RideService @Transactional
    participant FC as FareCalculator
    participant SS as SurgeStrategy
    participant CS as CouponService
    participant DB as MySQL

    U->>C: POST /api/rides/{id}/end
    C->>RS: endRide(rideId)
    RS->>DB: SELECT ride FOR UPDATE
    RS->>RS: assert status == ONGOING
    RS->>RS: distanceKm = haversine(pickup, drop)

    RS->>FC: baseFare(distanceKm, billedCarType)
    Note over FC: Σ slabs, then max(minFare, slabSum)
    FC-->>RS: baseFare

    RS->>SS: multiplier(pickup, now)
    SS-->>RS: surge (1.00 default)

    RS->>CS: discountFor(couponCode, fareAfterSurge)
    CS-->>RS: discount (capped, never exceeds fare)

    RS->>DB: UPDATE ride SET status='COMPLETED', fare columns,<br/>active_user_id = NULL
    RS->>DB: UPDATE drivers SET status='AVAILABLE' WHERE id=?
    RS-->>C: FareBreakdown {base, surge, discount, total}
    C-->>U: 200 itemised breakdown
```

Both DB writes sit in one transaction: a ride never completes while its driver stays stuck `ON_TRIP`.

Returning an itemised **`FareBreakdown`** instead of a bare number is deliberate — it makes the pricing demo-able and the tests precise about which stage broke.

---

## 5. Fare pipeline

```mermaid
flowchart LR
    D["distanceKm"] --> S["Σ slab rates<br/>RateCard[billedCarType]"]
    S --> F["max(minFare, slabSum)"]
    F --> X["× surge multiplier"]
    X --> C["− coupon discount<br/>(capped at maxDiscount)"]
    C --> Z["max(0, fare)"]
    Z --> R["round HALF_UP, 2dp"]
    R --> OUT["FareBreakdown<br/>persisted on ride row"]

    RCARD[("RateCard config<br/>application.yml<br/>SEDAN · HATCHBACK · SUV…")] -.-> S
    RCARD -.-> F
```

Adding SUV = one config block. Adding a tier = one config row. No code path changes.

---

## 6. Data model

```mermaid
erDiagram
    USERS ||--o{ RIDES : books
    DRIVERS ||--o{ RIDES : serves
    COUPONS ||--o{ RIDES : "applied to"

    USERS {
        varchar id PK
        varchar name
        varchar phone UK
        timestamp created_at
    }
    DRIVERS {
        varchar id PK
        varchar name
        varchar car_type
        decimal rating
        decimal lat "indexed"
        decimal lng "indexed"
        varchar status "AVAILABLE|ON_TRIP|OFFLINE"
        timestamp location_updated_at
    }
    RIDES {
        varchar id PK
        varchar user_id FK
        varchar driver_id FK
        varchar requested_car_type
        varchar assigned_car_type
        decimal pickup_lat
        decimal pickup_lng
        decimal drop_lat
        decimal drop_lng
        varchar status "ONGOING|COMPLETED|CANCELLED"
        varchar coupon_code
        decimal distance_km
        decimal base_fare
        decimal surge_multiplier
        decimal discount
        decimal total_fare
        varchar active_user_id UK "= user_id while ONGOING, else NULL"
        varchar active_driver_id UK "= driver_id while ONGOING, else NULL"
        timestamp started_at
        timestamp ended_at
    }
    COUPONS {
        varchar code PK
        varchar type "PERCENT|FLAT"
        decimal value
        decimal max_discount
        timestamp expires_at
        boolean active
    }
```

**The `active_user_id` / `active_driver_id` trick:** MySQL unique indexes ignore `NULL`s. Setting these columns to the id while a ride is `ONGOING` and to `NULL` on completion makes "one active ride per user, one per driver" a **database invariant**, not a service-layer hope. Two concurrent bookings for the same rider cannot both survive commit.

All money columns are `DECIMAL(10,2)`; all coordinates `DECIMAL(9,6)`.

---

## 7. State machines

```mermaid
stateDiagram-v2
    direction LR
    state "Ride" as R {
        [*] --> ONGOING: book() — driver reserved
        ONGOING --> COMPLETED: endRide() — fare charged
        ONGOING --> CANCELLED: cancel() — fee per policy
        COMPLETED --> [*]
        CANCELLED --> [*]
    }
```

```mermaid
stateDiagram-v2
    direction LR
    state "Driver" as D {
        [*] --> AVAILABLE: register()
        AVAILABLE --> ON_TRIP: conditional UPDATE wins
        ON_TRIP --> AVAILABLE: endRide() / cancel()
        AVAILABLE --> OFFLINE: goOffline()
        OFFLINE --> AVAILABLE: goOnline()
    }
```

Driver status changes only through a **conditional UPDATE with a status predicate** — never `SELECT` then `save()`. That single rule removes double-booking without a global lock.

---

## 8. API surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/users` | Register a user |
| `POST` | `/api/drivers` | Register a driver (name, carType, location, rating) |
| `PATCH` | `/api/drivers/{id}/location` | Update cab location |
| `PATCH` | `/api/drivers/{id}/status` | Go online / offline |
| `POST` | `/api/rides` | Book a ride |
| `POST` | `/api/rides/{id}/end` | End ride → `FareBreakdown` |
| `POST` | `/api/rides/{id}/cancel` | Cancel ride (bonus) |
| `GET` | `/api/users/{id}/rides?status=ONGOING\|COMPLETED` | Rider history |
| `GET` | `/api/drivers/{id}/rides?status=…` | Driver history |
| `POST` | `/api/coupons` | Add coupon |
| `DELETE` | `/api/coupons/{code}` | Delete coupon |
| `GET` | `/api/fare/estimate` | Quote without booking (nice-to-have) |

**Error contract:** domain exceptions map through `GlobalExceptionHandler` to `{code, message}` — `NO_DRIVER_AVAILABLE` → 409, `INVALID_COUPON` → 400, `RIDE_NOT_FOUND` → 404, `ILLEGAL_RIDE_STATE` → 409, `DUPLICATE_ACTIVE_RIDE` → 409, validation failure → 400 with field errors.

---

## 9. Key trade-offs

| Decision | Chosen | Rejected | Why |
|---|---|---|---|
| Storage | **MySQL 8 + Spring Data JPA** | In-memory maps (spec permits) | A real DB makes the hard guarantees — atomic reservation, unique active ride — enforceable and demonstrable instead of asserted. |
| Schema management | Hibernate `ddl-auto=update` for the exercise | Flyway migrations | Flyway is correct for production and is named in "what I'd do with more time"; writing migrations inside a 2-hour budget buys nothing for the grade. |
| Concurrency | Conditional `UPDATE … WHERE status='AVAILABLE'` (DB-level CAS) | `synchronized`, or `SELECT` + `save()`, or `@Version` retry | Single round trip, no lock held across the match, correct across multiple app instances. Optimistic-lock retry solves the same problem with more code and more failure modes. |
| Rider double-booking | Nullable unique column (`active_user_id`) | Service-level "check then insert" | Check-then-insert is a race. The unique index is the only thing that actually holds under load. |
| Driver geo search | Bounding-box prefilter on indexed `lat`/`lng`, then `ST_Distance_Sphere` | Full scan + Java haversine; or geohash / spatial index | Index-assisted and readable. `POINT` + `SPATIAL INDEX` is the documented scale answer, contained in one repository method. |
| Module shape | Package-by-feature in one Maven module, boundaries by convention | Multi-module Maven reactor | Same boundary discipline, none of the reactor setup cost. Promotion to real modules is mechanical. |
| Money | `BigDecimal` + `DECIMAL(10,2)`, HALF_UP | `double` / `FLOAT` | Floating-point money in a pricing round is an automatic finding. |
| Upgrade billing | Bill `requestedCarType`, assign `assignedCarType` | Bill assigned type then discount back | Two explicit columns make "free upgrade" a data fact, testable in one assertion, with no compensating arithmetic. |
| Strategy selection | Spring `Map<String, Strategy>` + property | `if/else` inside `RideService` | Swapping nearest ↔ highest-rated must not touch booking logic — stated bonus requirement. |
| Strings | `StringUtils` (commons-lang3) everywhere | Hand-rolled null/blank/case checks | One consistent null-safe idiom; no `NullPointerException` surface in coupon-code and phone handling. |
| Distance | Haversine, pickup → drop | Routed road distance | No routing service available offline; `DistanceCalculator` is the seam for the real one. |

---

## 10. What this design does not do (and would, with more time)

- **No Flyway migrations** — schema comes from `ddl-auto=update`; production needs versioned migrations and `ddl-auto=validate`.
- **No idempotency keys** — a retried `POST /api/rides` can create a second ride once the first completes.
- **Geo search is bounding-box + trig**, not a spatial index or geohash bucket; fine at demo scale, replaced inside one repository method.
- **Surge ships as `NoSurgeStrategy` plus one demand/supply implementation**; a real one needs a rolling demand window per geo cell and a cap.
- **No outbox / event log** — a real system emits ride lifecycle events for fare audit and analytics.
- **No auth, no rate limiting, no pagination** on history endpoints.
- **Read scaling untouched** — history queries hit the primary; real deployments read from a replica.
