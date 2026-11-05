# CardDemo online (CICS) re-platform plan

Re-platforms the 17 online CICS programs in `app/cbl` and their BMS maps in `app/bms` as a
Spring Boot 3 / Java 17 REST API (`api/`) on PostgreSQL plus a React + TypeScript Vite front end
(`web/`). The batch programs (CBACT0x, CBTRN0x, CBSTM03x) are migrated separately in the
`app/java/carddemo-batch` PRs and are out of scope; this module does not depend on them.
COBOL sources, copybooks and sample data are read-only inputs.

## Mapping conventions

| CICS / BMS concept | Re-platform |
| --- | --- |
| COMMAREA user id / type (`CDEMO-USER-ID`, `CDEMO-USER-TYPE`) | HMAC-signed bearer token issued at sign-on (`SessionTokenService`); controllers take a `SessionUser` argument |
| `EIBCALEN = 0` (no commarea) | HTTP 401 with `nextProgram = COSGN00C` (`SessionAuthFilter`) |
| `ERRMSGO` / `WS-MESSAGE` | `ScreenResponse.message` with the literal COBOL text, `messageType` ERROR/SUCCESS/INFO |
| `MOVE -1 TO xxxL` (cursor on bad field) | `ScreenResponse.field` (camelCase request property name) |
| `XCTL PROGRAM(...)` / `RETURN TRANSID` | `ScreenResponse.nextProgram`; the UI maps it to a route via `web/src/programs.ts` |
| ENTER | the main POST/PUT/GET of the resource |
| PF3 (return) | client-side navigation to `CDEMO-FROM-PROGRAM` (menu); no API call |
| PF4 (clear) | client-side form reset |
| PF5 / PF12 (save / cancel on update screens) | PUT on save; cancel is client-side reload |
| PF7 / PF8 (STARTBR + READPREV/READNEXT) | keyset pagination: `?after=<lastKey>` / `?before=<firstKey>` returning `KeysetPage` |
| `READ ... UPDATE` + `REWRITE` + `WRITE` | one Spring `@Transactional` service method |
| Validation error HTTP status | 400 (`CardDemoException.validation`), 404 for NOTFND, 409 for DUPKEY/DUPREC |

Deliberate deviation: USRSEC passwords are stored as BCrypt hashes (`password_hash`) and never
returned by the API. Sign-on compares against the hash; user update requires the password field
to be re-entered (COUSR02C already rejects a blank password).

## Schema (Flyway `V1__carddemo_schema.sql`)

| Table | VSAM file / copybook | Key |
| --- | --- | --- |
| `user_security` | USRSEC / CSUSR01Y | `usr_id` |
| `customer` | CUSTDATA / CVCUS01Y | `cust_id` |
| `account` | ACCTDATA / CVACT01Y | `acct_id` |
| `card` | CARDDATA / CVACT02Y | `card_num` (+ index on `acct_id`, the CARDAIX path) |
| `card_xref` | CARDXREF / CVACT03Y | `card_num` (+ index on `acct_id`, the CXACAIX path) |
| `transaction` | TRANSACT / CVTRA05Y | `tran_id` (+ index on `card_num`) |
| `tran_type` | TRANTYPE / CVTRA03Y | `tran_type_cd` |
| `tran_category` | TRANCATG / CVTRA04Y | (`tran_type_cd`, `tran_cat_cd`) |
| `tran_cat_balance` | TCATBALF / CVTRA01Y | (`acct_id`, `tran_type_cd`, `tran_cat_cd`) |
| `disclosure_group` | DISCGRP / CVTRA02Y | (`acct_group_id`, `tran_type_cd`, `tran_cat_cd`) |

`SampleDataLoader` (enabled with `CARDDEMO_LOAD_DATA=true`) imports `app/data/ASCII/*.txt` or the
fixed-width `app/data/EBCDIC/*.PS` files (code page 037, zoned-decimal overpunch signs) into empty
tables. USRSEC exists only in EBCDIC. TRANSACT is seeded from `dailytran` because
`DALYTRAN.PS.INIT` holds a single empty record. Data quirks preserved as-is: the sample
ACCTDATA puts the group code (`A000000000`) in the `ACCT-ADDR-ZIP` slot, and the ASCII and EBCDIC
files differ in two records (asserted in `SampleDataLoaderIT`).

Feature groups that need extra tables add their own migration in a reserved version range
(`spring.flyway.out-of-order` is enabled so ranges can merge in any order).

## Endpoints and screens

All paths are under `/api`; every response is a `ScreenResponse<T>`. Lists use `KeysetPage<T>`.

| Group | Program (tran) | BMS map | Endpoint(s) | Page | Flyway range | Java package / web folder |
| --- | --- | --- | --- | --- | --- | --- |
| Sign-on + menus | COSGN00C (CC00) | COSGN00 | `POST /api/auth/signon`, `POST /api/auth/signoff` | `/signon` | V2_x | `signon` / `features/signon` |
| | COMEN01C (CM00) | COMEN01 | `GET /api/menu`, `POST /api/menu/select` | `/menu` | | |
| | COADM01C (CA00) | COADM01 | `GET /api/admin/menu`, `POST /api/admin/menu/select` | `/admin` | | |
| Accounts | COACTVWC (CAVW) | COACTVW | `GET /api/accounts/{acctId}` (account + customer via xref) | `/accounts/view` | V3_x | `account` / `features/accounts` |
| | COACTUPC (CAUP) | COACTUP | `GET /api/accounts/{acctId}`, `PUT /api/accounts/{acctId}` (account + customer, one tx) | `/accounts/update` | | |
| Cards | COCRDLIC (CCLI) | COCRDLI | `GET /api/cards?acctId=&cardNum=&after=&before=` (7 rows) | `/cards` | V4_x | `card` / `features/cards` |
| | COCRDSLC (CCDL) | COCRDSL | `GET /api/cards/{cardNum}?acctId=` | `/cards/view` | | |
| | COCRDUPC (CCUP) | COCRDUP | `PUT /api/cards/{cardNum}` | `/cards/update` | | |
| Transactions | COTRN00C (CT00) | COTRN00 | `GET /api/transactions?startTranId=&after=&before=` (10 rows) | `/transactions` | V5_x | `transaction` / `features/transactions` |
| | COTRN01C (CT01) | COTRN01 | `GET /api/transactions/{tranId}` | `/transactions/view` | | |
| | COTRN02C (CT02) | COTRN02 | `POST /api/transactions` (acct or card via xref, confirm Y/N, next id = max + 1) | `/transactions/add` | | |
| Bill pay | COBIL00C (CB00) | COBIL00 | `GET /api/bill-payments/{acctId}`, `POST /api/bill-payments` (balance update + transaction insert, one tx) | `/billpay` | V6_x | `billpay` / `features/billpay` |
| Reports | CORPT00C (CR00) | CORPT00 | `POST /api/reports` (monthly / yearly / custom range, confirm); persists a `report_request` row in place of the TDQ `JOBS` job submission | `/reports` | V7_x | `report` / `features/reports` |
| User admin | COUSR00C (CU00) | COUSR00 | `GET /api/users?startUserId=&after=&before=` (10 rows) | `/users` | V8_x | `user` / `features/users` |
| | COUSR01C (CU01) | COUSR01 | `POST /api/users` | `/users/add` | | |
| | COUSR02C (CU02) | COUSR02 | `GET /api/users/{userId}`, `PUT /api/users/{userId}` | `/users/update` | | |
| | COUSR03C (CU03) | COUSR03 | `GET /api/users/{userId}`, `DELETE /api/users/{userId}` | `/users/delete` | | |

Admin-only (`SessionUser.requireAdmin`): `/api/admin/**` and `/api/users/**`.
Menu options pointing at programs outside this scope (COPAUS0C, COTRTLIC, COTRTUPC) return the
COBOL "not installed" style message rather than being implemented.

## Delivery

1. Foundation PR (this module skeleton): plan, schema, entities, loader, session, error envelope,
   Testcontainers base, Vite shell with placeholder routes, CI.
2. Six parallel feature PRs, one per group above. Each owns only its Java package, its
   `web/src/features/<group>` folder, its Flyway range and its tests:
   JUnit 5 + Testcontainers ITs for the happy path and every COBOL validation branch, and Vitest
   tests for its forms.
3. Integration PR: Dockerfiles, root `docker-compose.yml` (PostgreSQL + API + web), README run
   instructions and the COBOL-program -> endpoint -> page traceability table.
