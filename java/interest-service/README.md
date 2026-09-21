# interest-service

Spring Boot 3 / Spring Batch 5 reimplementation of the CardDemo COBOL interest calculator
`app/cbl/CBACT04C.cbl` (JCL `app/jcl/INTCALC.jcl`, STEP15).

## Layout

```
com.carddemo.interest
  trace/       @Trace annotation: COBOL program/copybook + paragraph + line references
  domain/      1:1 copybook records (BigDecimal for all V99 fields, scale 2)
  calc/        InterestCalculator - pure arithmetic of 1300-COMPUTE-INTEREST
  repository/  keyed access interfaces for DISCGRP, ACCTFILE, XREFFILE
  service/     InterestRateService (1200-*), TransactionFactory (1300-B-WRITE-TX),
               AccountPostingService (1050-UPDATE-ACCOUNT, 1400-COMPUTE-FEES)
  batch/       InterestProcessor (control-break main loop), InterestJobConfig (job wiring)
TRACEABILITY.md   COBOL -> Java matrix
src/test/resources/fixtures/   golden input/expected data
```

## Build / test

Requires JDK 21. Maven is provided via the wrapper.

```
cd java/interest-service
./mvnw -q test
```

If Maven Central answers `429 Too Many Requests` from your network, point Maven at the Google
mirror in `~/.m2/settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>central-google</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven-central.storage-download.googleapis.com/maven2</url>
    </mirror>
  </mirrors>
</settings>
```

## Run

Job `interestCalcJob` takes the same inputs as JCL STEP15. `runDate` is the JCL `PARM`
(`2022071800`); the four input files are the ASCII fixed-width layouts from `app/data/ASCII`
(one record per line). The account file is rewritten in place (VSAM `REWRITE`), transactions are
appended to `tranFile` (SYSTRAN).

```
cp ../../app/data/ASCII/acctdata.txt /tmp/acctdata.txt
./mvnw spring-boot:run -Dspring-boot.run.arguments="runDate=2022071800 \
  tcatbalFile=../../app/data/ASCII/tcatbal.txt xrefFile=../../app/data/ASCII/cardxref.txt \
  discgrpFile=../../app/data/ASCII/discgrp.txt acctFile=/tmp/acctdata.txt tranFile=/tmp/systran.txt"
```

Step `interestCalcStep`: `FlatFileItemReader<TranCatBalance>` -> `InterestProcessor` (stateful
control-break, one instance per step execution) -> `InterestItemWriter` (SYSTRAN
`FlatFileItemWriter<Transaction>` + account `REWRITE`). `InterestStepListener` loads the keyed
files before the step and, after the last chunk, performs the COBOL end-of-file branch
(`InterestProcessor.flush()` = final `1050-UPDATE-ACCOUNT`) and writes the account file back.

## Conventions

- `BigDecimal` everywhere, never `double`. COBOL `COMPUTE` without `ROUNDED` truncates: use scale 2 and `RoundingMode.DOWN`.
- Every class/method that mirrors COBOL carries `@Trace(...)` and appears in `TRACEABILITY.md`.
- Test methods are named after the COBOL paragraph they validate.

## Child-session workflow

The module was built by a coordinator session plus parallel child sessions branching from the
scaffolding branch:

| Child | Scope | Files |
|---|---|---|
| A | Domain records + fixed-width parsing/formatting | `domain/**`, `src/test/.../domain/**` |
| B | `InterestCalculator` + unit tests | `calc/**`, `src/test/.../calc/**` |
| C | Golden fixture extraction | `src/test/resources/fixtures/**` |
| D | Traceability matrix | `TRACEABILITY.md` |

The coordinator then implemented services, the control-break processor, the Spring Batch job, and
integration/parity tests.
