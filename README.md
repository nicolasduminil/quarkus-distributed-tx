# Quarkus XA — distributed money transfer with Narayana 2PC

A small Quarkus workshop demonstrating a **genuine two-phase commit (2PC)** across
two XA datasources, coordinated by Narayana. A money transfer atomically debits
the source account, credits the destination account and records the transfer;
if anything fails (e.g. insufficient funds) **every** datasource rolls back.

This is the XA/2PC baseline that the planned LRA/Saga variant will be compared against.

## Architecture

```
Transfer service (XA coordinator)  ──  one @Transactional
 ├─ accounts datasource (XA)  : debit(source) + credit(destination)
 └─ xfers   datasource (XA)  : insert Transfer(COMPLETED)
Narayana: prepare(accounts) + prepare(xfers)  →  commit both, or rollback both
```

Real 2PC requires the coordinator to hold the resource managers, so the
transactional path is co-located in the transfer service, which owns both XA
datasources. The account service remains a separate, standalone microservice for
account CRUD.

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
boundaries, and it is precisely what the planned LRA/Saga variant will avoid, at
the cost of trading atomicity for eventual consistency with compensations.

## Modules

| Module               | Role                                                                                                                                                                           |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`             | Shared library: `Common` utility, the application exception hierarchy (`ApplicationException`, `AccountNotFoundException`, `InsufficientFundsException`, `InvalidAmountException`) and the single `ApplicationExceptionMapper` that maps them to HTTP status codes. |
| `account-repository` | Shared persistence layer for accounts: the `Account` entity, `AccountDto`, the `AccountRepository` interface and its two implementations. Used by **both** applications, which is what lets the transfer service enlist the `accounts` datasource in its own JTA transaction. |
| `account`            | Standalone account microservice (port 8081). Owns the `accounts` schema. Full account CRUD plus debit/credit endpoints.                                                          |
| `transfer`           | Transfer microservice / XA coordinator (port 8080). Two XA datasources (`accounts`, `xfers`). Transfer CRUD plus the 2PC transfer operation.                                     |

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

- `MoneyTransferIT` (transfer): happy path, `unknownAccountShouldReturn404`,
  transfer CRUD, and `failedTransferShouldRollBackBothDatasources`, which proves
  atomicity — after an overdrawing transfer returns `400`, both the balances
  (accounts DS) and the transfer log (xfers DS) are unchanged.
- `AccountResourceIT` (account): account CRUD, debit/credit, 404 on unknown account,
  and rejection of overdrafts and invalid amounts.

## Continuous integration

`.circleci/config.yml` runs `mvn -B verify` on every push, with the Maven repository
cached on the `pom.xml` checksum. It uses a **machine** executor rather than a Docker
one because the integration tests start two PostgreSQL containers via Testcontainers.

## Run the full stack

```bash
# Build the container images (container-image.build=true is enabled)
mvn clean package
docker compose up
```

`docker-compose.yaml` starts two PostgreSQL instances — `accounts-db` (5432) and
`xfers-db` (5433), each with `max_prepared_transactions=100` (required for XA
prepared transactions) — plus the `account` and `transfer` services, both running
under the `prod` profile.

## Notes

- PostgreSQL used as an XA resource manager must run with
  `max_prepared_transactions > 0`, otherwise the 2PC prepare phase fails at
  runtime. This is set for Dev Services (`...devservices.command`) and in Compose.
- `quarkus.transaction-manager.enable-recovery=true` enables the Narayana
  recovery manager so in-doubt transactions are resolved after a crash.
- `AccountRestClient` in the `account` module is a leftover from the REST-based
  iteration described above and is no longer wired to anything.
