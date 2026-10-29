# MJ MASALA — API

The backend for the MJ MASALA storefront: Java 17 · Spring Boot 3.3 · Spring Data JPA /
Hibernate · Spring Security with JWT · Flyway · PostgreSQL (MySQL supported) · OpenAPI.

It covers everything the React frontend does today — catalogue and filtering, the reviews
system, the cart, wishlist, coupons, checkout and orders — plus the back-office endpoints
a shop needs to run on it.

---

## Quick start

### With Docker (PostgreSQL + API)

```bash
cp .env.example .env          # then edit JWT_SECRET and ADMIN_PASSWORD
docker compose up --build
```

The API comes up on <http://localhost:8080/api>, Swagger UI on
<http://localhost:8080/api/swagger-ui.html>. Flyway creates the schema and the seeder loads
the catalogue on first boot.

### Locally

You need JDK 17 and a PostgreSQL database.

```bash
createdb mj_masala

export DB_URL=jdbc:postgresql://localhost:5432/mj_masala
export DB_USER=mj
export DB_PASSWORD=mj
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_PASSWORD='something-strong'

mvn spring-boot:run
```

### Tests

```bash
mvn test
```

77 tests, no database required — they run against in-memory H2 in PostgreSQL mode.

---

## Configuration

Everything is environment-overridable; the defaults in `application.yml` are for local work
only.

| Variable | Default | Notes |
| --- | --- | --- |
| `PORT` | `8080` | |
| `DB_URL` | `jdbc:postgresql://localhost:5432/mj_masala` | swap for the MySQL URL below |
| `DB_USER` / `DB_PASSWORD` | `mj` / `mj` | |
| `JWT_SECRET` | a placeholder | **must** be set in any real environment, ≥ 32 bytes |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:4173,http://localhost:3000` | comma-separated |
| `ADMIN_EMAIL` | `admin@mjmasala.in` | first admin, seeded once |
| `ADMIN_PASSWORD` | `ChangeMe@123` | **change it** — the seeder logs a warning while it is default |
| `SPRING_PROFILES_ACTIVE` | `dev` | |
| `JWT_SECRET` | | access tokens live 2 h, refresh tokens 14 d (see `app.jwt`) |

Delivery rules are configuration, not code: `app.shipping.free-above` (999),
`app.shipping.standard-fee` (79) and `app.shipping.cod-fee` (40).

### MySQL is the default

`application-dev.yml` ships pointed at MySQL, and `docker-compose.yml` brings up a MySQL 8
container. Switching vendors is a datasource swap and nothing else:

| | |
| --- | --- |
| `application-dev.yml` | the datasource URL, driver and Hibernate dialect |
| `docker-compose.yml` | the database service |
| `pom.xml` | nothing to do — both drivers already ship |
| the migrations | nothing to do — both vendor folders already ship, and Flyway resolves `{vendor}` from the driver |

The compose file sets `utf8mb4` deliberately: the catalogue carries Devanagari names and
the rupee sign, and MySQL's 3-byte `utf8` collation would mangle them.

**The two migrations are held to the entity model by tests.** `MySqlSchemaParityTest` and
`PostgresSchemaParityTest` ask Hibernate to emit the schema it would create for that
dialect, then compare it against the hand-written migration column by column. Both pass
with 19 tables and zero differences.

That check earned its place: it caught **11 columns in the MySQL migration declared as
`varchar` where MySQLDialect wants a native `enum`** (`app_user.role`, `orders.status`,
`product.kind` and eight more). Because the app boots with `ddl-auto=validate`, that would
have failed at startup on MySQL — and only on MySQL, which is why it survived every earlier
run on H2. PostgreSQL was already correct: its dialect uses `varchar` plus a
`check (... in ...)` constraint.

---

## Database

Hibernate runs with **`ddl-auto: validate`** — it never creates or alters anything. Flyway
owns the schema:

```
src/main/resources/db/migration/
├── postgresql/V1__schema.sql    19 tables, indexes, foreign keys
└── mysql/V1__schema.sql         the same, in MySQL dialect
```

`spring.flyway.locations=classpath:db/migration/{vendor}` picks the right one.

### Seed data

`resources/seed/catalogue.json` and `resources/seed/reviews.json` are **exported from the
frontend's own data modules**, not retyped — so the API and the UI agree on every price,
name and review instead of drifting apart. The seeder loads them into an empty database
(17 products with their four-pack ladders, 7 combos, 334 reviews, 4 coupons, 1 admin) and
then recalculates each product's rating from the rows it just inserted.

It is idempotent — it does nothing if any product already exists. Turn it off with
`app.seed.enabled=false` once real data is in.

### Two deliberate denormalisations

`product.min_price`, `product.off_pct`, `product.rating_avg` and `product.review_count` are
derived columns, recomputed by `Product.recalculatePackAggregates()` and
`ReviewService.recalculate()`. They exist so the shop can filter and sort in SQL rather
than loading every pack and review for every card. They are never set by hand.

`off_pct` is deliberately the discount on the *cheapest* pack — the same number the card
prints — so the "biggest saving" sort cannot order products by a figure the card never
shows.

---

## API

Base path `/api`. Interactive docs at `/api/swagger-ui.html`.

### Public

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/products` | shop grid — `category`, `q`, `kind`, `maxPrice`, `minRating`, `bestseller`, `sort`, `page`, `size` |
| `GET` | `/products/{slug}` | product with pack ladder and spec table |
| `GET` | `/categories` | the shelves |
| `GET` | `/combos` · `/combos/{slug}` | combo boxes |
| `GET` | `/products/{id}/reviews` | paged reviews + summary + distribution (`sort=recent\|helpful\|highest\|lowest`) |
| `GET` | `/products/{id}/reviews/summary` | average, total, 5→1 buckets |
| `POST` | `/coupons/validate` | preview a code against a subtotal |
| `GET` | `/shipping/pincode/{pin}` | serviceability, ETA, COD availability |
| `GET` | `/shipping/rules` | free-delivery threshold |
| `POST` | `/wholesale/enquiries` · `/contact/messages` | the two public forms |
| `POST` | `/auth/register` | create an account and send a verification code |
| `POST` | `/auth/verify` | check the code and sign in |
| `POST` | `/auth/resend` | another sign-up code |
| `POST` | `/auth/forgot-password` | start a reset (never reveals whether the account exists) |
| `POST` | `/auth/reset-password` | finish a reset and sign in |
| `POST` | `/auth/login` · `/auth/refresh` | sign in, renew |

### Authenticated (`Authorization: Bearer <token>`)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/auth/me` | the signed-in account |
| `GET` `POST` `PATCH` `DELETE` | `/cart`, `/cart/items`, `/cart/items/{id}` | server-side basket |
| `GET` `POST` `DELETE` | `/wishlist`, `/wishlist/{id}`, `/wishlist/status` | saved items |
| `POST` | `/orders` | place an order |
| `GET` | `/orders/mine`, `/orders/{reference}` | order history |
| `POST` | `/products/{id}/reviews` · `/reviews/{id}/helpful` | write a review, vote |
| `DELETE` | `/reviews/{id}` | remove your own review |

### Admin (`ROLE_ADMIN`)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/admin/stats` | dashboard numbers |
| `GET` `POST` | `/admin/products` | list everything (including hidden), create one with its pack ladder |
| `POST` `DELETE` | `/admin/products/{slug}/image` | upload or remove the product photograph |
| `POST` `DELETE` | `/admin/combos/{slug}/image` | the same for a combo |
| `GET` | `/media/{filename}` | serve an uploaded photograph (public, cached for a year) |
| `GET` `PUT` `DELETE` | `/admin/products/{slug}` | read for the edit form, replace, soft-delete |
| `PATCH` | `/admin/products/{slug}` · `/admin/packs/{id}` | quick toggles: live/hidden, a single pack price |
| `GET` `POST` | `/admin/combos` | list everything, create a box |
| `GET` `PUT` | `/admin/combos/{slug}` | read for the edit form, replace price and contents |
| `GET` | `/admin/orders` · `PATCH /admin/orders/{reference}` | fulfilment |
| `GET` | `/admin/payments` · `/admin/payments/summary` | the payments panel and its totals |
| `PATCH` | `/admin/payments/{reference}` | settle, fail or refund |
| `GET` `POST` `PATCH` | `/admin/coupons` | manage discount codes |
| `GET` `PATCH` | `/admin/enquiries` | work the wholesale pipeline |

The storefront's admin panel (`#/admin` in the React app) drives all of this. Two rules it
relies on are enforced here rather than in the form:

- **A pack's MRP can never be below its selling price** — `AdminCatalogueService` rejects
  it, so a bad payload cannot make the shop print a negative discount.
- **The derived columns are refreshed on every write.** `minPrice`, `offPct` and
  `ratingAvg` are recomputed whenever a product is saved, which is what makes a price
  change in the panel show up immediately in the shop's filters and sorts.

One implementation note worth keeping: replacing a product's pack ladder clears the old
rows and inserts new ones, and **the delete is flushed before the insert**. Pack SKUs are
unique and Hibernate orders its statements inserts-first, so without that flush re-adding
the same SKU collides with the row it is replacing.

---

## Security

- **Stateless JWT.** No session cookie, so CSRF protection is off deliberately — there is
  no ambient credential for a cross-site request to ride on. The trade-off is documented in
  `SecurityConfig`.
- **Access vs refresh.** The token carries a `typ` claim; the filter only accepts `access`,
  so a stolen refresh token cannot call the API. Refresh tokens are only good for
  `/auth/refresh`.
- **Authorisation is declared once** in `SecurityConfig`, and `/admin/**` is additionally
  guarded by `@PreAuthorize` so a future refactor of the matchers cannot silently open it.
- **Passwords** are BCrypt (cost 10). Login failures return one message whether the email
  exists or not.
- **Orders are scoped to their owner** — the lookup is by reference *and* user id, so a
  guessed reference is a 404 rather than somebody else's delivery address.
- **Prices are never read from the request.** A client sends which SKUs and how many; the
  server prices the order from the catalogue. A tampered payload can change what is bought,
  never what it costs.

---

### Sign-up is two steps

`POST /auth/register` creates the account **switched off** and returns a `pendingToken`
plus the destinations masked — no session. Only `POST /auth/verify` returns tokens. The
pending token names the account being verified, so the verify endpoint never looks an
account up by email and therefore cannot be used to probe which addresses are registered.

Codes are generated from a `SecureRandom`, stored only as a BCrypt hash, expire in ten
minutes, are single-use, and stop being accepted after five wrong attempts. Resends are
rate-limited. A code issued for `REGISTER` is refused for `RESET`.

**Resetting a password ends existing sessions.** Every token carries a `ver` claim and the
user row holds `token_version`, which is bumped on a password change — a refresh token from
before the change no longer matches. This is a counter rather than a timestamp on purpose:
a JWT's `iat` only has second resolution, so a token minted in the same second as the change
would be neither clearly before nor clearly after it.

### Delivering the code

Both channels are **off by default**, so a fresh clone boots and logs the code instead of
failing to reach a mail host that is not there. Turning one on is configuration, not code:

```bash
# email, over any SMTP host
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_FROM=no-reply@mjmasala.in

# SMS, through a provider's HTTP API
SMS_ENABLED=true
SMS_PROVIDER=msg91          # msg91 | fast2sms | generic
SMS_URL=https://api.msg91.com/api/v5/flow/
SMS_AUTH_KEY=...
SMS_SENDER_ID=MJMSLA
SMS_TEMPLATE_ID=...         # Indian carriers require a registered DLT template
```

`RoutingNotificationSender` picks the channel and falls back to logging when one is not
configured. A provider failure is logged and swallowed rather than thrown — a flaky mail
host must not turn into a broken sign-up.

`app.notifications.sms.provider` chooses the request shape; **add a case to
`SmsNotificationSender.requestFor`** for anybody else. Nothing outside that class knows how
the message travels.

Startup logs which channels are live:

```
One-time code delivery: email=on sms=off (logging)
```

**Delivery is proved, not assumed.** `EmailDeliveryTest` runs a real SMTP server in-process
(GreenMail) and `SmsDeliveryTest` stubs the provider with the JDK's own HTTP server — in
both, `app.otp.expose-code` is **off**, so the only way to learn the code is to read it out
of the delivered message. If delivery broke, those tests could not pass.

**Turn `OTP_EXPOSE_CODE` off** as soon as a provider is live. It returns the code in the API
response, which is useful for testing the flow and a hole anywhere else. The app logs a
warning if both are on at once.

### Product photography

Uploaded in the admin panel and served from `/media/{filename}`. Four decisions worth
keeping:

- **The filename is generated, never taken from the upload.** A client-supplied name is a
  path traversal and an overwrite waiting to happen.
- **Every upload gets a new name.** Reusing it would leave browsers and CDNs serving the
  replaced photo — the most confusing bug in an image pipeline. A fresh name is its own
  cache-buster, which is why `/media` can be cached for a year and marked immutable.
- **The bytes are inspected, not the header.** `Content-Type` is whatever the client typed;
  JPEG, PNG and WebP are detected from their magic bytes. A renamed script is refused.
- **The old file is deleted only after the new one is stored**, so a failed upload never
  leaves a product with no picture.

Images are scaled down so the long edge is at most `app.media.max-dimension` (1600 by
default) using the JDK's own image support — no image library to add. On a runtime without
AWT the original is stored unchanged rather than the upload failing: a big photo is a
performance problem, a failed upload is a broken admin panel.

**Storage is behind `MediaStorage`.** The shipped implementation writes to
`app.media.dir` — put that on a mounted volume in production, because a container's own
filesystem does not survive a redeploy. Moving to S3 means adding one implementation and
marking it `@Primary`; nothing above it changes.

`imageUrl` on the catalogue DTOs is the public path (`/api/media/...`). It is a path, not a
host, because the API does not know whether it sits behind a proxy or a CDN — the frontend
resolves it against its own API base.

### One thing to know about ids

Products and combos are separate tables with separate sequences, so **product 1 and combo 1
both exist**. A client must key catalogue items by type *and* id — the storefront's
`catalogKey()` does exactly that. This is not a bug to fix in SQL; it is a property of
having two entity types, and any client that assumes one id space will break.

---

## Connecting the frontend

The storefront already has `src/lib/api.js` wired with the right paths, an axios instance
and a request interceptor that attaches `localStorage['mj-token']` as a bearer token.

```bash
# frontend/.env.local
VITE_API_BASE_URL=http://localhost:8080/api
```

Then switch the components over one at a time — `USE_API` in `lib/api.js` is the flag.
The two are built to agree: the seed data is exported from the frontend, and the query
parameters on `GET /products` mirror the shop's filter panel one for one.

**Status: the catalogue reads are wired and verified.** `lib/apiClient.js` and
`hooks/useCatalogue.js` in the storefront switch the shop grid, product detail, combos,
search, the 3D range viewer and the home page onto this API. With `VITE_API_BASE_URL`
unset the same code runs on the bundled catalogue, so the standalone preview still works;
if the API is configured but unreachable it falls back to the bundle rather than rendering
an empty shop.

Verified end to end: with the API up, turmeric's card shows **26 reviews** (the real count
in the database) instead of the bundled 214; with the API blocked it shows 214 and does not
crash.

Two things still to change when the cart moves over:

1. **Ratings are already real.** The API returns the count of reviews that exist, so the
   storefront's hand-written marketing figures (turmeric's "214") are replaced by the true
   number wherever the API is the source.
2. **The cart is still local.** It runs on `localStorage` in both modes. The endpoints here
   return the whole cart on every write, so the client can render the response rather than
   recompute totals — but the routes need a bearer token, and the storefront has no sign-in
   screen yet. That is the next slice.

---

## Testing

`mvn test` — 77 tests, H2 in PostgreSQL mode, no external services.

| Class | What it pins down |
| --- | --- |
| `AuthFlowTest` (7) | registration, duplicate email, login, opaque failures, token type rules, malformed tokens |
| `CatalogueApiTest` (11) | paging, every filter, sorting, search, detail, combos, 404s |
| `OrderFlowTest` (12) | cart totals, COD fee, free-delivery threshold, coupons, validation, order scoping, auth walls |
| `ReviewFlowTest` (8) | summary buckets, one-review-per-account, helpful idempotency, delete scoping, sorting |
| `ProductImageTest` (7) | upload, non-images refused, oversized refused, replace deletes the old file, removal clears it, path traversal blocked, customers locked out |
| `EmailDeliveryTest` (3) | **a real SMTP server** — the code arrives by email and is what verifies the account |
| `SmsDeliveryTest` (2) | **a real outbound HTTP call** — the provider request carries the right headers and body |
| `AuthFlowTest` (15) | the whole account lifecycle: sign-up, verification, sign-in, reset, purpose binding, session invalidation |
| `AdminApiTest` (10) | product and combo creation, price and discount changes reaching the shop, MRP validation, the order and payment panels, customers locked out |
| `HttpSmokeTest` (5) | the real server over the wire — context path, UTF-8, bearer auth, JSON errors |
| `SchemaMigrationTest` (2) | **Flyway applies the migration and Hibernate validates every entity against it** |
| `MySqlSchemaParityTest` (1) | the MySQL migration matches Hibernate's MySQL schema, column for column |
| `PostgresSchemaParityTest` (1) | the same for PostgreSQL |

`SchemaMigrationTest` is the one to keep: it turns Flyway on, points it at the PostgreSQL
migration and leaves `ddl-auto=validate` on, so any drift between the hand-written schema
and the entities fails the build instead of failing on the first deployment.

Bugs this suite caught while the backend was being written:

- `MultipleBagFetchException` — fetching a combo's items and specs in one query.
- A UTF-8 trap where `₹` in a generated display string was decoded as Latin-1 by a client
  that assumed the wrong charset — `HttpSmokeTest` now asserts the rupee sign survives a
  real HTTP round trip.
- An inconsistency where the shop sorted by the best discount on any pack but the card
  printed the discount on the cheapest pack — two different numbers for the same product.
- A unique-constraint violation when a product's pack ladder was replaced: Hibernate
  inserts before it deletes, so re-adding the same SKU collided with the row it was
  replacing. The fix is an explicit flush after the clear.
- Spec rows silently dropped on product creation, because the create path only handled
  packs.

---

## What is not real yet

Being explicit, so nothing here is mistaken for production-ready:

- **No payment gateway.** `paymentStatus` stays `PENDING`; there is no Razorpay/Stripe
  integration. The order is recorded and priced correctly, which is the part that had to be
  right first.
- **Pincode serviceability is a placeholder.** `ShippingService` uses a plausible rule (all
  Indian pins, ETA by zone) so the UI has something real to render. Swap that one method for
  the courier's API — the response shape already matches what the frontend expects.
- **No email or SMS.** Order confirmations, OTPs and wholesale replies need a provider.
- **Images are served by the frontend bundle**, not the API. Product rows carry an
  `imageKey`; there is no upload endpoint.
- **Product creation is not exposed.** Admin endpoints cover the day-to-day edits (price,
  availability, coupons, orders); creating a product with its pack ladder is the next
  addition, and the entities already model it.
- **No rate limiting** on login or review endpoints. Put the API behind a gateway or add
  Bucket4j before it faces the internet.

---

## Layout

```
src/main/java/in/mjmasala/api/
├── domain/        19 JPA entities + 9 enums
├── repository/    Spring Data repositories + the shop's filter specifications
├── dto/           request/response records, grouped by area
├── service/       business logic — pricing, coupons, orders, reviews, catalogue
├── security/      JWT issue/verify, the auth filter, the principal
├── web/           REST controllers
├── config/        security, CORS, OpenAPI, typed properties, the seeder
├── exception/     typed failures + one global handler
└── util/          money and formatting helpers
```
