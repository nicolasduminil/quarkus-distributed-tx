# Quarkus Distributed TX — the same money transfer, twice

A Quarkus workshop that implements one money-transfer use case **two ways**, so the
two dominant answers to distributed consistency can be compared side by side on
identical code:

- **XA / 2PC** (`transfer`, port 8080) — a genuine two-phase commit across two XA
  datasources, coordinated by Narayana.
- **Saga / MicroProfile LRA** (`transfer-lra`, port 8090) — compensation-based
  consistency across autonomous services, coordinated by a Narayana LRA coordinator.

Both expose the **same `/xfers` REST contract** and are driven by the **same shared
test suite** (`transfer-tck`), so any difference you observe is a difference in
transaction strategy and nothing else.

> **Status.** Both stacks are complete and fully tested — `mvn clean verify` runs
> 18 integration tests across the two implementations, none skipped.

## Architecture

```
Transfer service (XA coordinator)  ──  one @Transactional
 ├─ accounts datasource (XA)  : debit(source) + credit(destination)
 └─ xfers   datasource (XA)  : insert Transfer(COMPLETED)
Narayana: prepare(accounts) + prepare(xfers), then commit both or roll back both
```

Real 2PC requires the coordinator to hold the resource managers, so the
transactional path is co-located in the transfer service, which owns both XA
datasources. The account service remains a separate, standalone microservice for
account CRUD, and separately acts as a saga participant for the LRA stack.

## Where Narayana actually drives the two-phase commit

There is no "XA component" class in this project — nothing implements or invokes
2PC by hand. The whole protocol is driven by the Narayana transaction manager
embedded in the transfer service, and the only thing that triggers it is a single
annotation. The chain is:

**1. The transaction begins** — `transfer/…/transfer/service/TransferService.java`

```java
@Transactional
public TransferDto processTransfer(TransferRequest request)
```

`@Transactional` is intercepted by ArC, which asks the Narayana `TransactionManager`
(from `quarkus-narayana-jta`, declared in the parent POM) to begin a JTA transaction
on the calling thread.

**2. Two XA resources are enlisted in that one transaction**

Inside that method two *different* datasources are touched:

| Operation | Repository | Datasource |
|---|---|---|
| `getAccount(...)`, then `debit`/`credit` (flushed by Hibernate dirty checking) | `AccountRepositoryXA` | `accounts` |
| `createTransfer(...)` | `TransferRepositoryXA` | `xfers` |

Both are declared XA in `transfer/src/main/resources/application.properties`:

```properties
quarkus.datasource.accounts.jdbc.transactions=xa
quarkus.datasource.xfers.jdbc.transactions=xa
```

That flag is the whole trick. Agroal then hands out `XAConnection`s instead of plain
ones, and on the first use of each pool inside the transaction it calls
`Transaction.enlistResource(xaResource)`. Narayana now has **two** branches
registered under one `Xid`.

**3. Commit time — the two phases**

When `processTransfer` returns normally, the interceptor calls `commit()` and
Narayana runs:

- **Prepare** — `XAResource.prepare()` on both branches; each PostgreSQL driver
  issues `PREPARE TRANSACTION '<xid>'`. This is why both databases must run with
  `max_prepared_transactions > 0`.
- **Log** — if both vote `XA_OK`, Narayana writes the commit decision to its object
  store (`quarkus.transaction-manager.object-store.directory`). That record is the
  point of no return.
- **Commit** — `XAResource.commit()` on both; each database issues
  `COMMIT PREPARED '<xid>'`.

Any negative vote, or the `InsufficientFundsException` thrown before the writes,
rolls back both branches. With `quarkus.transaction-manager.enable-recovery=true`
the Narayana recovery manager replays the object store to resolve in-doubt branches
if the JVM dies between prepare and commit.

## The saga alternative

Where the XA stack co-locates the resource managers, the LRA stack does the
opposite: `transfer-lra` has **no access to the accounts database at all** and can
only ask the account service over HTTP. Consistency is no longer atomic — it is
reconstructed afterwards by a coordinator driving every participant to the same
outcome.

```
transfer-lra (LRA initiator)  ──  @LRA(REQUIRED) on POST /xfers
 ├─ account service, over HTTP : reserve debit  (balance untouched)
 ├─ account service, over HTTP : reserve credit (balance untouched)
 └─ own xfers datasource       : Transfer(PENDING)

On success the coordinator closes the LRA, calls @Complete on every
participant, and the reservations are applied.
On any exception it cancels instead, calls @Compensate on every
participant, and the reservations are released.
```

**Reserve-then-confirm, not act-then-compensate.** The naive saga debits
immediately and credits the money back on failure. That is wrong twice over: the
balance visibly dips and recovers, which any concurrent reader can observe, and
the compensating credit can itself fail and strand the saga. Here a debit only
records a PENDING `Reservation`; `@Complete` applies it and `@Compensate` drops
it. Because the balance never moved, **there is no compensating write that can
fail**. Available funds are `balance − Σ pending debits`, exposed at
`GET /lra/accounts/{id}/availability`.

**Participants must be idempotent.** A coordinator retries `@Complete` and
`@Compensate` until it gets an answer, so a participant that applies its effect
twice corrupts the balance. Both participants key their work on the LRA id and
treat any non-PENDING state as already handled, and both expose `@Status` so the
coordinator can re-ask after a crash. This is the part that is usually skipped and
the part that actually bites in production.

### What actually differs

| | XA / 2PC | Saga / LRA |
|---|---|---|
| Coordinator | Embedded (Narayana JTA) | **Separate process** that must call participants back |
| Service autonomy | None — shared accounts database | Full — only the account service touches balances |
| Isolation | Total; nothing observable until commit | A reservation **is** observable while in flight |
| Failure | Rollback leaves **no trace** | Leaves a `COMPENSATED` audit row |
| Coupling | Both services must be up and enlisted | Participants can be completed later |
| Blocking | Resources locked between prepare and commit | No locks held across the saga |

That fourth row is why the shared contract counts *completed* transfers rather
than all rows: "we tried and undid it" is a real saga outcome, not a failure to
paper over.

## Why not XA over REST?

An earlier iteration had the transfer service call the account service's
`/accounts/{id}/debit` and `/credit` endpoints from inside a `@Transactional`
method (a `TransactionalAccountClient`). **That was not XA and gave no atomicity**:
plain HTTP does not propagate a transaction context, so the remote `credit` had
already committed in the account service's own transaction by the time the local
one rolled back. The `Synchronization` it registered only logged, and
`setRollbackOnly()` could not undo remote work. It has been removed deliberately.

The current design gets real atomicity by making the transfer service the
coordinator that reaches both databases directly, over two XA datasources, in one
JVM. The price is architectural: the transfer service reads and writes the
`accounts` database rather than going through the account service's API — the two
services share a database. That coupling is inherent to XA across service
boundaries, and it is precisely what the LRA/Saga variant above avoids, at the
cost of trading atomicity for eventual consistency with compensations.

## Modules

| Module               | Role                                                                                                                                                                           |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`             | Shared library: `Common` utility, the transfer DTOs (`TransferDto`, `TransferRequest`, `TransferResponse`, `TransferStatus`), the application exception hierarchy and the single `ApplicationExceptionMapper` that maps them to HTTP status codes. |
| `account-repository` | Shared persistence layer for accounts: the `Account` entity, `AccountDto`, the `AccountRepository` interface and its two implementations. Depended on by `account` and by `transfer` — and pointedly **not** by `transfer-lra`. |
| `transfer-tck`       | The REST contract both transfer implementations must satisfy, as an abstract test class. Test tooling is at compile scope so consumers depend on it with `<scope>test</scope>`.   |
| `account`            | Account microservice (port 8081). Owns the `accounts` schema. CRUD + debit/credit, **plus** the LRA participant endpoints under `/lra/accounts`.                                  |
| `transfer`           | XA coordinator (port 8080). Two XA datasources (`accounts`, `xfers`). Transfer CRUD plus the 2PC transfer operation.                                                             |
| `transfer-lra`       | Saga orchestrator (port 8090). One plain datasource for its own transfer log; reaches balances only through the account service's API.                                           |

Each repository has two implementations: an in-memory one (`DefaultAccountRepository`,
`DefaultTransferRepository`) and a JPA/Panache one (`AccountRepositoryXA`,
`TransferRepositoryXA`). Both are `@Alternative`, so neither is enabled by default;
each application picks the XA pair explicitly through
`quarkus.arc.selected-alternatives`. Only the XA implementations participate in 2PC —
the in-memory ones are there to run the services without a database.

All four modules carry the Jandex plugin so their `@Entity`, `@Provider` and CDI bean
classes are indexed when consumed as external dependencies.

## Endpoints

Account service (`http://localhost:8081`, Swagger UI at `/q/swagger-ui`):

- `GET /accounts`, `GET /accounts/{accountId}`, `POST /accounts`, `PUT /accounts/{accountId}`, `DELETE /accounts/{accountId}`
- `POST /accounts/{accountId}/debit`, `POST /accounts/{accountId}/credit` (amount in the body)

Transfer service (`http://localhost:8080`, Swagger UI at `/q/swagger-ui`):

- `POST /xfers` — perform a transfer (`{sourceAccountId, destinationAccountId, amount, currency}`)
- `GET /xfers`, `GET /xfers/{transferId}`, `PUT /xfers/{transferId}`, `DELETE /xfers/{transferId}`

## Build & test

Integration tests use Quarkus Dev Services (Testcontainers), so a running Docker
daemon is required; no manual database setup is needed. Each service seeds its own
data in dev/test (`import.sql` for `account`, `import-accounts.sql` for `transfer`);
in the `prod` profile the account service owns the `accounts` schema and no
generation or seeding happens.

```bash
mvn clean verify
```

- `AbstractMoneyTransferContractIT` (transfer-tck): the shared scenarios — happy
  path, insufficient funds leaving everything unchanged, unknown account, listing
  and deletion. Assertions are on **deltas**, and the balance/count probes are
  **abstract**, because the XA service can read the databases directly while the
  saga service can only ask over HTTP. Failure paths assert the **response body**
  as well as the status — both stacks must return the business error rather than
  transaction plumbing, which a status-only assertion would not catch.
- `MoneyTransferIT` (transfer): the contract against XA, with repository-based probes.
- `MoneyTransferSagaIT` (transfer-lra): the contract against LRA, with HTTP probes,
  plus saga-only assertions about the `COMPENSATED` audit row and released reservations.
- `AccountResourceIT` (account): account CRUD, debit/credit, 404 on unknown account,
  and rejection of overdrafts and invalid amounts. Unchanged by the LRA work, which
  is how we know the participant endpoints were added additively.

### Gotcha: a participant must not cancel the saga

`@LRA`'s `end = false` only stops a method ending the LRA **on success**. The default
`cancelOnFamily = {CLIENT_ERROR, SERVER_ERROR}` still applies, so a participant that
answers 400 cancels the entire saga on its own initiative. The orchestrator then
tries to cancel an LRA the coordinator has already discarded, gets a 503, and the
LRA filter **replaces your business error with `CancelFailed`** — a correct status
code wrapped around a useless body.

Participant methods here therefore use:

```java
@LRA(value = LRA.Type.MANDATORY, end = false, cancelOnFamily = {})
```

A participant reports; the orchestrator decides. The tell-tale in the coordinator
log is `Cannot find transaction id: ...` at the moment the initiator tries to
cancel.

### Gotcha: `@Consumes` on the LRA callbacks

An LRA participant class typically carries a class-level
`@Consumes(MediaType.APPLICATION_JSON)` for its business endpoints. The coordinator
does **not** send JSON when it calls `@Complete`/`@Compensate`/`@Status`/`@Forget`,
so JAX-RS rejects those callbacks with **415 Unsupported Media Type** before your
code ever runs. Nothing is logged on the participant side, the LRA simply hangs in
`Closing` forever, and the saga looks broken for no visible reason.

Every callback here therefore carries an explicit `@Consumes(MediaType.WILDCARD)`.
If a saga ever stalls with reservations that never settle, the first thing to check is the
coordinator's own log:

```bash
docker logs lra-coordinator | grep "LRAParticipantRecord.doEnd"
# ...put http://accounts:8081/lra/accounts/complete... failed with status: 415
```

The coordinator's REST API is the other diagnostic worth knowing —
`GET :50000/lra-coordinator` lists LRAs and their status, so a stuck saga shows up
as `"status":"Closing","recovering":true`.

## Continuous integration

`.circleci/config.yml` runs `mvn -B verify` on every push, with the Maven repository
cached on the `pom.xml` checksum. It uses a **machine** executor rather than a Docker
one because the integration tests start two PostgreSQL containers via Testcontainers.

## Run the full stack

```bash
# Build the container images (container-image.build=true is enabled)
mvn clean package
docker compose up -d
```

`docker-compose.yaml` starts the whole comparison:

| Service | Port | Role |
|---|---|---|
| `accounts-db` | 5432 | Accounts, `max_prepared_transactions=100` for XA |
| `xfers-db` | 5433 | XA transfer log, `max_prepared_transactions=100` |
| `xfers-lra-db` | 5434 | Saga transfer log (no XA needed) |
| `lra-coordinator` | 50000 | `quay.io/jbosstm/lra-coordinator` |
| `account` | 8081 | CRUD + LRA participant |
| `transfer` | 8080 | XA/2PC |
| `transfer-lra` | 8090 | Saga/LRA |

Both transfer services run against the same accounts, so you can drive the same
transfer through each and compare:

```bash
BODY='{"sourceAccountId":"ACC1","destinationAccountId":"ACC2","amount":10.00,"currency":"EUR"}'
curl -s -XPOST -H 'Content-Type: application/json' -d "$BODY" http://localhost:8080/xfers  # XA
curl -s -XPOST -H 'Content-Type: application/json' -d "$BODY" http://localhost:8090/xfers  # Saga
curl -s http://localhost:8081/lra/accounts/ACC1/availability                                # reservations
```

Note `QUARKUS_LRA_BASE_URI` on the two participants: it is the address the
coordinator uses to call them back, and without it a participant advertises a URL
that is only meaningful from its own side of the network.

## Notes

- PostgreSQL used as an XA resource manager must run with
  `max_prepared_transactions > 0`, otherwise the 2PC prepare phase fails at
  runtime. This is set for Dev Services (`...devservices.command`) and in Compose.
- `quarkus.transaction-manager.enable-recovery=true` enables the Narayana
  recovery manager so in-doubt transactions are resolved after a crash.
- `AccountRestClient` in the `account` module is a leftover from the REST-based
  iteration described above and is no longer wired to anything.
