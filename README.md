# Dine-In Restaurant Ordering and Inventory System

A web application that digitizes the internal operations of a **dine-in** restaurant: table-based
ordering, real-time kitchen coordination, billing and payment, and ingredient-level inventory
tracking with availability validation and low-stock alerts.

A waiter opens an order against a table and adds dishes; every addition is checked against the
menu item's availability **and** the ingredient stock its recipe consumes, so an order that cannot
actually be cooked is refused at the point of entry. Confirming the order pushes it onto the
kitchen's live queue, where it is advanced through _preparing_ and _ready_. The cashier settles the
bill, and that single transaction records the payment, marks the order paid, frees the table and
deducts every ingredient the order consumed — all together, or not at all. The manager sees the
money and the stock afterwards in a date-ranged dashboard.

Built as a university software-engineering course project, so the design is as much the deliverable
as the behaviour: layered architecture, SOLID, and a deliberate, documented use of design patterns. The authoritative requirements live in
[Project_SRS.docx](Project_SRS.docx).

**Out of scope by design:** online delivery or takeout, refunds and returns, supplier/purchase-order
integration, and any modification of an order after payment.

**Status: complete.** All functional requirements (FR-01–FR-24) are implemented and every
non-functional requirement (NFR-01–NFR-07) is backed by an automated test or a recorded measurement
— 319 tests in the default build plus 21 integration tests against real PostgreSQL.

---

## Features

Access is role-based and enforced by the server on **every** endpoint, independently of what the UI
chooses to show (SRS §2.1):

| Module            | Manager | Waiter | Kitchen | Cashier |
| ----------------- | :-----: | :----: | :-----: | :-----: |
| Menu Management   |  Full   |  Read  |    –    |    –    |
| Table & Orders    |  Read   |  Full  |  Read   |  Read   |
| Kitchen Queue     |    –    |   –    |  Full   |    –    |
| Billing & Payment |  Read   |   –    |    –    |  Full   |
| Inventory         |  Full   |   –    |    –    |    –    |
| Reports & Alerts  |  Full   |   –    |    –    |    –    |
| User Management   |  Full   |   –    |    –    |    –    |

**Authentication & user management** — JWT sign-in with BCrypt-hashed passwords; there is no
self-signup, the manager pre-registers every account. Accounts can be created, edited, have their
password reset, and be enabled or disabled — never deleted, so the history they are attached to
stays intact. A disabled or demoted account loses its access on the **next request**, not when its
token eventually expires.

**Menu management** — categories and items with price, description and an availability flag; each
item carries a _recipe_: the ingredients and quantities one serving consumes. Managers edit,
waiters browse.

**Table & order lifecycle** — a floor grid of tables (`AVAILABLE` / `OCCUPIED` / `NEEDS_SERVICE`),
each occupied card showing the live status of the order sitting on it. Items are added with a
quantity and optional kitchen notes, adjusted with `+`/`−`, and are **locked the moment the order is
confirmed**. Line prices are snapshots, so editing the menu later never moves an existing order's
total.

**Order-item validation** — every addition passes a chain of validators: the dish must be available,
and there must be enough ingredient stock for the requested quantity _plus what the rest of the same
order has already committed_. A failed check rejects the item and leaves the order untouched.

**Kitchen queue** — a money-free, kitchen-only display of confirmed and preparing tickets, oldest
first, with a ticket-age badge that warms in colour as an order waits. One tap per ticket to start
cooking and to mark it ready; it refreshes on its own within a couple of seconds.

**Billing & payment** — a bill computed from the order (subtotal → tax → service charge → grand
total), settled by cash, card or mobile, ending on a printable receipt at its own reopenable URL.
Settlement freezes the amounts _and the rates that produced them_, so a later rate change cannot
rewrite history.

**Automatic inventory deduction** — recording a payment deducts every ingredient the order consumed,
in the same transaction, under row-level locks taken in a fixed order. Stock can never go below zero
(enforced in the database _and_ the application); if stock has run out since the order was taken the
payment is refused outright — nothing charged, nothing deducted, and the message names the
ingredient and the shortfall.

**Inventory & alerts** — ingredients with a unit, current level and low-stock threshold; every
manual stock change is written to an **append-only audit log** (who, which ingredient, signed
quantity, reason, when) that cannot be edited or deleted through any route, including raw SQL.

**Reports & dashboard** — date-ranged sales totals, revenue broken down by menu category, top
sellers by quantity, and a live low-stock panel. Every figure is read from the frozen payment
record, never recomputed, and dates are calendar days in the restaurant's own timezone.

---

## Tech Stack

| Layer       | Technology                                                                            |
| ----------- | ------------------------------------------------------------------------------------- |
| Frontend    | **Angular 18** SPA — standalone components, signals, lazy-loaded routes, Lucide icons |
| Backend     | **Spring Boot 3.3** REST API — **Java 21**, Maven                                     |
| Database    | **PostgreSQL** hosted on **NeonDB** (serverless, SSL-required)                        |
| Migrations  | **Flyway** (`V1`–`V8`, version-controlled)                                            |
| Persistence | Spring Data JPA / Hibernate                                                           |
| Security    | Spring Security, JWT bearer tokens (JJWT), BCrypt, `@PreAuthorize` method security    |
| Testing     | JUnit 5, AssertJ, Mockito, MockMvc + `spring-security-test`, **ArchUnit**             |

Architecture is strictly layered — Angular → REST controllers → services → repositories — with DTOs
at the HTTP boundary and entities never leaking past the service layer. The layering is not a
convention here but a test: **ArchUnit rules fail the build if it is violated.**

---

## Installation & Running

### Prerequisites

- **JDK 21** (e.g. Eclipse Temurin) with `JAVA_HOME` set
- **Maven 3.9+**
- **Node.js 20 or 22 LTS** + npm
- A **NeonDB** project (free tier is enough) — no local database installation required

### 1. Configure the database connection

Copy the template and fill in your Neon credentials — this file is gitignored and never committed:

```bash
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml
```

Use the JDBC string from Neon → _Connection Details_, keeping `?sslmode=require` and preferring the
pooled `-pooler` host:

```
jdbc:postgresql://<endpoint>-pooler.<region>.aws.neon.tech/<db>?sslmode=require
```

Alternatively set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` as environment variables instead.

### 2. Start the backend (from `backend/`)

```bash
mvn spring-boot:run
```

- API base URL: `http://localhost:8080/api`
- Flyway applies the migrations on startup; Hibernate only ever _validates_ the schema
- Smoke check: `GET http://localhost:8080/api/ping` → `{"status":"ok"}`
- Health: `GET http://localhost:8080/api/actuator/health`

### 3. Start the frontend (from `frontend/`)

```bash
npm install
npm start
```

- Dev server: `http://localhost:4200`
- `/api/*` is proxied to the backend on `:8080` (see `frontend/proxy.conf.json`)
- The login screen appears; signing in lands on the home screen for that role

### Default accounts (dev seed)

Seeded idempotently on first backend startup. All share the password `password123` — **change these
before any non-local deployment.**

| Username  | Role    |
| --------- | ------- |
| `manager` | MANAGER |
| `waiter`  | WAITER  |
| `kitchen` | KITCHEN |
| `cashier` | CASHIER |

### Running the tests

```bash
# from backend/
mvn test          # 319 tests: unit, ArchUnit layering rules, and the full RBAC sweep
mvn test -Pit     # 21 more: the invariants that need real PostgreSQL, run against Neon

# from frontend/
npm test          # Karma unit tests
npm run build     # production build
```

`mvn test` deliberately excludes the `integration`-tagged tests so it stays fast. They are separate
because row locks, `CHECK` constraints and the append-only trigger are exactly what an in-memory
database gets wrong — there would be no point testing them anywhere but real PostgreSQL.

### Configuration reference

All of these are environment variables with working defaults (see `application.yml`):

| Variable                                 | Default                 | Purpose                                                                                                 |
| ---------------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | —                       | NeonDB connection                                                                                       |
| `SERVER_PORT`                            | `8080`                  | API port                                                                                                |
| `BILLING_TAX_RATE`                       | `0.05`                  | Tax as a fraction of subtotal                                                                           |
| `BILLING_SERVICE_CHARGE_RATE`            | `0.10`                  | Service charge as a fraction of subtotal                                                                |
| `REPORTING_ZONE`                         | `Asia/Dhaka`            | The **restaurant's** timezone for report calendar days                                                  |
| `CORS_ALLOWED_ORIGINS`                   | `http://localhost:4200` | Origins permitted to call the API                                                                       |
| `JWT_SECRET`                             | dev-only placeholder    | HS256 signing key (≥32 bytes; the app refuses to start under the `prod` profile if left at the default) |
| `JWT_EXPIRATION_MINUTES`                 | `480`                   | Token lifetime — one 8-hour shift                                                                       |

---

## API Endpoints

Base path `/api`. All responses are JSON. Every endpoint except the two marked _public_ requires an
`Authorization: Bearer <token>` header, and every one is guarded server-side by the roles listed —
an unauthorized caller gets `401`/`403` regardless of what the client exposes.

### Authentication

| Method | Path          | Roles    | Description                            |
| ------ | ------------- | -------- | -------------------------------------- |
| `POST` | `/auth/login` | _public_ | Exchange username + password for a JWT |
| `GET`  | `/auth/me`    | all      | The signed-in user's identity and role |
| `GET`  | `/ping`       | _public_ | Liveness smoke check                   |

### Menu Management

| Method   | Path                                     | Roles           | Description                          |
| -------- | ---------------------------------------- | --------------- | ------------------------------------ |
| `GET`    | `/menu/categories`                       | Manager, Waiter | List categories                      |
| `POST`   | `/menu/categories`                       | Manager         | Create a category                    |
| `PUT`    | `/menu/categories/{id}`                  | Manager         | Update a category                    |
| `DELETE` | `/menu/categories/{id}`                  | Manager         | Delete a category                    |
| `GET`    | `/menu/items?categoryId=&availableOnly=` | Manager, Waiter | List menu items, optionally filtered |
| `GET`    | `/menu/items/{id}`                       | Manager, Waiter | One menu item                        |
| `POST`   | `/menu/items`                            | Manager         | Create a menu item                   |
| `PUT`    | `/menu/items/{id}`                       | Manager         | Update a menu item                   |
| `PATCH`  | `/menu/items/{id}/availability`          | Manager         | Toggle availability                  |
| `DELETE` | `/menu/items/{id}`                       | Manager         | Delete a menu item                   |
| `GET`    | `/menu/items/{itemId}/recipe`            | Manager         | The item's ingredient recipe         |
| `PUT`    | `/menu/items/{itemId}/recipe`            | Manager         | Replace the recipe                   |

### Inventory

| Method   | Path                                      | Roles   | Description                                       |
| -------- | ----------------------------------------- | ------- | ------------------------------------------------- |
| `GET`    | `/inventory/ingredients`                  | Manager | List ingredients with stock levels                |
| `GET`    | `/inventory/ingredients/low-stock`        | Manager | Ingredients at or below their threshold           |
| `POST`   | `/inventory/ingredients`                  | Manager | Create an ingredient                              |
| `PUT`    | `/inventory/ingredients/{id}`             | Manager | Update name, unit or threshold                    |
| `DELETE` | `/inventory/ingredients/{id}`             | Manager | Delete an ingredient                              |
| `POST`   | `/inventory/ingredients/{id}/adjustments` | Manager | Adjust stock (signed quantity + reason) — audited |
| `GET`    | `/inventory/ingredients/{id}/adjustments` | Manager | The append-only adjustment history                |

### Tables & Orders

| Method   | Path                          | Roles   | Description                                                |
| -------- | ----------------------------- | ------- | ---------------------------------------------------------- |
| `GET`    | `/tables`                     | all     | Floor state, with each table's active order and its status |
| `PATCH`  | `/tables/{id}/service-flag`   | Waiter  | Raise or clear the _needs service_ flag                    |
| `GET`    | `/orders?status=&tableId=`    | all     | List orders, optionally filtered                           |
| `GET`    | `/orders/{id}`                | all     | One order with its items                                   |
| `POST`   | `/orders`                     | Waiter  | Open an order on a table (table becomes occupied)          |
| `POST`   | `/orders/{id}/items`          | Waiter  | Add an item — availability + stock validated               |
| `PATCH`  | `/orders/{id}/items/{itemId}` | Waiter  | Change a line's quantity                                   |
| `DELETE` | `/orders/{id}/items/{itemId}` | Waiter  | Remove a line                                              |
| `POST`   | `/orders/{id}/confirm`        | Waiter  | Send to the kitchen; items lock                            |
| `POST`   | `/orders/{id}/prepare`        | Kitchen | Start cooking                                              |
| `POST`   | `/orders/{id}/ready`          | Kitchen | Mark ready for collection                                  |
| `POST`   | `/orders/{id}/serve`          | Waiter  | Mark delivered to the table                                |

Item edits are accepted **only while the order is `PENDING`**. Transitions are separate action
endpoints rather than one generic status update, so the kitchen cannot reach a transition that
belongs to the waiter.

### Kitchen Queue

| Method | Path             | Roles   | Description                                              |
| ------ | ---------------- | ------- | -------------------------------------------------------- |
| `GET`  | `/kitchen/queue` | Kitchen | Confirmed + preparing tickets, oldest confirmation first |

Kitchen-only — stricter than Table & Orders, and it carries no prices at all.

### Billing & Payment

| Method | Path                           | Roles            | Description                                                            |
| ------ | ------------------------------ | ---------------- | ---------------------------------------------------------------------- |
| `GET`  | `/billing/orders`              | Manager, Cashier | Orders awaiting settlement                                             |
| `GET`  | `/billing/orders/{id}/bill`    | Manager, Cashier | Computed bill: subtotal, tax, service charge, total                    |
| `GET`  | `/billing/orders/{id}/receipt` | Manager, Cashier | The receipt for a settled order                                        |
| `POST` | `/billing/orders/{id}/payment` | Cashier          | Record payment — settles, frees the table and deducts stock atomically |

### Reports & Alerts

| Method | Path                                  | Roles   | Description                                               |
| ------ | ------------------------------------- | ------- | --------------------------------------------------------- |
| `GET`  | `/reports/sales?from=&to=`            | Manager | Revenue, orders completed, and the per-category breakdown |
| `GET`  | `/reports/top-items?from=&to=&limit=` | Manager | Best-selling dishes by quantity                           |

`from` and `to` are inclusive `YYYY-MM-DD` calendar days in the restaurant's configured timezone.

### User Management

| Method | Path                   | Roles   | Description                                  |
| ------ | ---------------------- | ------- | -------------------------------------------- |
| `GET`  | `/users`               | Manager | List staff accounts                          |
| `POST` | `/users`               | Manager | Register a staff account                     |
| `PUT`  | `/users/{id}`          | Manager | Update name or role                          |
| `POST` | `/users/{id}/password` | Manager | Reset a password                             |
| `POST` | `/users/{id}/enable`   | Manager | Re-enable an account                         |
| `POST` | `/users/{id}/disable`  | Manager | Retire an account — takes effect immediately |

---

## Project Members

Department of Computer Science & Engineering, Shahjalal University of Science & Technology, Sylhet.

| #   | Name                  | Registration No. | Role                                                                                                        |
| --- | --------------------- | ---------------- | ----------------------------------------------------------------------------------------------------------- |
| 1   | Md. Pranta            | 2022331050       | Menu management & inventory module — categories, items, ingredients, recipes, stock adjustments             |
| 2   | Syeda Maisha Anika    | 2022331026       | Table & order lifecycle — order state machine, item validation chain, waiter floor view and order builder   |
| 3   | Md. Farhan Hasin Anik | 2022331024       | Kitchen queue & billing — pass display, bill computation, payment settlement and atomic inventory deduction |
| 4   | Shimul Das            | 2022331016       | Reporting & dashboard — sales aggregates, category breakdown, top sellers, low-stock alerts                 |
| 5   | Abdullah Al Mamun     | 2022331080       | Architecture & security — layering, auth/RBAC, cross-cutting hardening, testing strategy                    |

---

## Project Workflow

### The restaurant workflow

```
  Waiter                      Kitchen                  Cashier              Manager
    │                            │                        │                    │
    ├─ open order on a table     │                        │                    │
    ├─ add items ──── validated against availability + stock                   │
    ├─ confirm ─────────────────►│                        │                    │
    │                            ├─ start cooking         │                    │
    │                            ├─ mark ready ───┐       │                    │
    ├─ serve ◄───────────────────────────────────-┘       │                    │
    │                                                     │                    │
    └─ order handed to the till ────────────────────────► ├─ take payment      │
                                                          │   ├─ freeze amounts│
                                                          │   ├─ free table    │
                                                          │   └─ deduct stock  │
                                                          └─ print receipt ───►├─ reports & alerts
```

**Order lifecycle:** `PENDING → CONFIRMED → PREPARING → READY → SERVED → PAID`
**Table lifecycle:** `AVAILABLE` ⇄ `OCCUPIED` / `NEEDS_SERVICE`
**Payment methods:** `CASH`, `CARD`, `MOBILE`

1. **Open** — the waiter taps a free table; a `PENDING` order is created and the table becomes
   occupied.
2. **Add items** — quantity plus optional kitchen notes, with a live subtotal. Each addition is
   validated for menu availability and for sufficient ingredient stock; a rejection explains itself
   and leaves the order unchanged. Identical items with identical notes merge into one line.
3. **Confirm** — the order joins the kitchen queue and its items are locked. There is no way back.
4. **Cook** — the kitchen advances the ticket to preparing, then ready; it leaves the queue at ready.
5. **Serve** — the waiter marks it served once it reaches the table.
6. **Settle** — the cashier picks the check, picks the method and confirms. In one transaction the
   payment is recorded, the order marked paid, the table released, and every recipe ingredient
   deducted under row locks. If anything fails, all of it rolls back.
7. **Review** — the manager's dashboard reads the frozen payment records for sales, category
   breakdown and top sellers, alongside a live low-stock panel that links straight to restocking.

Stock is deliberately **not reserved** at order time — payment is the single authoritative point of
deduction, so two tables can both order the last portion, and the second payment is what refuses.

### How the project was built

Eight milestones, each a **vertical slice** — migration → domain → repository → service → REST →
Angular UI — built and verified end-to-end before the next one started:

|     | Milestone           | Delivered                                                                                                       |
| --- | ------------------- | --------------------------------------------------------------------------------------------------------------- |
| M0  | Scaffolding         | Monorepo, Neon, Flyway, Angular shell                                                                           |
| M1  | Auth & RBAC         | JWT login, BCrypt, `@PreAuthorize`, Angular guards & interceptor                                                |
| M2  | Menu management     | Categories & items, Manager CRUD + Waiter browse                                                                |
| M3  | Inventory & recipes | Ingredients, audited adjustments, low-stock, recipes                                                            |
| M4  | Tables & orders     | Order state machine, validation chain, floor view & builder                                                     |
| M5  | Kitchen queue       | Kitchen-only queue projection, prepare/ready, polling pass display                                              |
| M6  | Billing & payment   | Computed bill, snapshotted payment, atomic locked deduction, receipt                                            |
| M7  | Reporting           | Sales summary, category breakdown, top sellers, low-stock dashboard                                             |
| M8  | Hardening           | Per-request authorization, database-enforced audit immutability, ArchUnit layering rules, exhaustive RBAC sweep |
