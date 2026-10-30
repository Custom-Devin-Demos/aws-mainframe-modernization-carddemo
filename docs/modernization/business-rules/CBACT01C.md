# CBACT01C — Account file read and reformat

Java: `app/java/carddemo-batch/jobs/cbact01c` (`com.carddemo.batch.cbact01c.Cbact01cJob`).

## 1. Purpose and JCL context

Job `READACCT` (`app/jcl/READACCT.jcl`): `PREDEL` (IEFBR14) deletes the previous output data sets,
then `STEP05` runs `CBACT01C`. The program browses the account master (KSDS) in key order,
DISPLAYs every record and writes three reformatted copies, one fixed-length, one with an
`OCCURS 5` table, one variable-length. It is a demo/utility job: no other CardDemo job reads its outputs
and it has no upstream batch dependency other than the account master being loaded.

DD names: `ACCTFILE` (input), `OUTFILE`, `ARRYFILE`, `VBRCFILE` (outputs), `SYSOUT`/`SYSPRINT`.

## 2. Inputs and outputs

| DD | Data set | Copybook / record | Organization, access | Java |
|----|----------|-------------------|----------------------|------|
| `ACCTFILE` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | CVACT01Y `ACCOUNT-RECORD` (300 bytes, key `ACCT-ID` 9(11)) | KSDS, sequential browse | `ctx.input("ACCTFILE")`, ASCII unload loaded into `KeyedFile` on `FD-ACCT-ID` |
| `OUTFILE` | `…ACCTDATA.PSCOMP` | `OUT-ACCT-REC`, FB 107; `OUT-ACCT-CURR-CYC-DEBIT` is COMP-3 | PS, sequential output | Parquet `account_out` |
| `ARRYFILE` | `…ACCTDATA.ARRYPS` | `ARR-ARRAY-REC`, FB 110; `ARR-ACCT-BAL OCCURS 5` (`ARR-ACCT-CURR-BAL` zoned, `ARR-ACCT-CURR-CYC-DEBIT` COMP-3), `ARR-FILLER` X(4) | PS, sequential output | Parquet `account_array` (occurrences flattened to `_1`…`_5`; filler dropped) |
| `VBRCFILE` | `…ACCTDATA.VBPS` | `VBR-REC` VB 10–80 (`WS-RECD-LEN`): `VBRC-REC1` (12) and `VBRC-REC2` (39) | PS, sequential output | Parquet `account_vbrc`, one row per record with `vbrc_record_type` (1/2) and `vbrc_record_length` (12/39) |
| `SYSOUT` | DISPLAY output | — | — | stdout + CSV `sysout` (`line_no`, `text`) |

Schemas: `jobs/cbact01c/src/main/resources/schemas/*.schema.json`. COMP-3 fields are published as
`decimal(12,2)` values; the packed encoding is not reproduced (design §5).

## 3. Processing rules

* **BR-CBACT01C-01** (PROCEDURE DIVISION main loop, `1000-ACCTFILE-GET-NEXT`): records are read
  sequentially in ascending `ACCT-ID` order, whatever the order of the unload, and each is fully
  processed (rules 03–10) before the next READ.
* **BR-CBACT01C-02** (`1000-ACCTFILE-GET-NEXT`): status `00` → `APPL-RESULT` 0, process; status `10`
  → `APPL-RESULT` 16, `END-OF-FILE` = `Y`, loop ends; any other status → 12, error (rule 12).
* **BR-CBACT01C-03** (`1100-DISPLAY-ACCT-RECORD`): for every record DISPLAY 11 lines
  `<field name padded to 24>:<field>` (`ACCT-ID` … `ACCT-GROUP-ID`, numerics as stored, zoned with
  overpunch) and a line of 49 dashes.
* **BR-CBACT01C-04** (`1300-POPUL-ACCT-RECORD`): `OUT-ACCT-ID`, status, current balance, credit
  limit, cash credit limit, open date, expiration date, current-cycle credit and group id are
  moved unchanged from the account record.
* **BR-CBACT01C-05** (`1300-POPUL-ACCT-RECORD`, `COBDATFT`): `ACCT-REISSUE-DATE` is moved to
  `CODATECN-INP-DATE` (X(20), space padded) and `WS-REISSUE-DATE`; with `CODATECN-TYPE` = `2` and
  `CODATECN-OUTTYPE` = `2` the assembler stores input bytes 1–4, 6–7, 9–10 in output bytes 1–8
  (`YYYY-MM-DD` → `YYYYMMDD`). Separators are not checked. The first 10 bytes of
  `CODATECN-0UT-DATE` go to `OUT-ACCT-REISSUE-DATE` (bytes 9–10 are never stored by COBDATFT and
  stay spaces). COBDATFT error path (`GOTOERR`): type not 1/2, type 1 with `-` in byte 5 or outtype 2,
  type 2 with outtype 1 → `CODATECN-ERROR-MSG` = `INVALID INPUT`, output untouched. CBACT01C always passes
  2/2 and never reads the message, so the error path cannot occur in this job.
* **BR-CBACT01C-06** (`1350-WRITE-ACCT-RECORD`): one OUTFILE record per account.
* **BR-CBACT01C-07** (`1300-POPUL-ACCT-RECORD`): if `ACCT-CURR-CYC-DEBIT` = 0,
  `OUT-ACCT-CURR-CYC-DEBIT` = 2525.00; otherwise the field is not moved (quirk Q1).
* **BR-CBACT01C-08** (`1000-ACCTFILE-GET-NEXT`, `1400-POPUL-ARRAY-RECORD`, `1450-WRITE-ARRY-RECORD`):
  `ARR-ARRAY-REC` is INITIALIZEd per account, then `ARR-ACCT-ID` = `ACCT-ID`; occurrence 1 =
  (`ACCT-CURR-BAL`, 1005.00); 2 = (`ACCT-CURR-BAL`, 1525.00); 3 = (-1025.00, -2500.00);
  4 and 5 = (0.00, 0.00). One ARRYFILE record per account.
* **BR-CBACT01C-09** (`1500-POPUL-VBRC-RECORD`): `VBRC-REC1` (INITIALIZEd per account) = `ACCT-ID`,
  `ACCT-ACTIVE-STATUS`; `VBRC-REC2` = `ACCT-ID`, `ACCT-CURR-BAL`, `ACCT-CREDIT-LIMIT`,
  `WS-ACCT-REISSUE-YYYY` (first 4 bytes of the unconverted reissue date). Both are DISPLAYed as
  `VBRC-REC1:<12 bytes>` and `VBRC-REC2:<39 bytes>`.
* **BR-CBACT01C-10** (`1550-WRITE-VB1-RECORD`, `1575-WRITE-VB2-RECORD`): two VBRCFILE records per
  account, length 12 (`VBRC-REC1`) then length 39 (`VBRC-REC2`).
* **BR-CBACT01C-11** (main loop, PROCEDURE DIVISION): DISPLAY `START OF EXECUTION OF PROGRAM CBACT01C` first;
  after each processed record DISPLAY the whole 300-byte `ACCOUNT-RECORD`; after end of file
  close ACCTFILE, DISPLAY `END OF EXECUTION OF PROGRAM CBACT01C`, GOBACK with RETURN-CODE 0.
  Output files are never CLOSEd explicitly (closed at step end). Counters (Java only):
  `acctfile_records_read`, `outfile_records_written`, `arryfile_records_written`,
  `vbrcfile_records_written`.

## 4. Error handling

* **BR-CBACT01C-12** (`0000-ACCTFILE-OPEN`, `2000-OUTFILE-OPEN`, `3000-ARRFILE-OPEN`,
  `4000-VBRFILE-OPEN`, `1000-ACCTFILE-GET-NEXT`, `1350`/`1450`/`1550`/`1575` WRITE paragraphs,
  `9000-ACCTFILE-CLOSE`): a file status other than `00` (and other than `10` for READ/WRITE)
  DISPLAYs a message, then `9910-DISPLAY-IO-STATUS` and `9999-ABEND-PROGRAM`:

  | Paragraph | Message |
  |-----------|---------|
  | 0000 | `ERROR OPENING ACCTFILE` |
  | 2000 / 3000 / 4000 | `ERROR OPENING OUTFILE<st>` / `ERROR OPENING ARRAYFILE<st>` / `ERROR OPENING VBRC FILE<st>` |
  | 1000 | `ERROR READING ACCOUNT FILE` |
  | 1350 / 1450 / 1550 / 1575 | `ACCOUNT FILE WRITE STATUS IS:<st>` |
  | 9000 | `ERROR CLOSING ACCOUNT FILE` |

* **BR-CBACT01C-13** (`9910-DISPLAY-IO-STATUS`): numeric status not starting with 9 →
  `FILE STATUS IS: NNNN00<st>`; otherwise `FILE STATUS IS: NNNN<stat1><binary value of stat2 as 999>`.
* `9999-ABEND-PROGRAM`: DISPLAY `ABENDING PROGRAM`, `CALL 'CEE3ABD'` with ABCODE 999, TIMING 0. Java throws
  `AbendException(999)`; the launcher exits non-zero and the state machine goes to `Abended`.
  Datasets opened so far are closed with the rows written before the abend; no `_manifest.json` is written.
* Java status mapping: missing ACCTFILE → `35`; output dataset cannot be created, write I/O failure, unload
  with a duplicate key or an over-long line, or a record whose CVACT01Y numerics cannot be decoded → `30`
  (GnuCOBOL also reports `30` for an output path it cannot create). Closing the in-memory KSDS cannot fail,
  so the `9000` error branch is ported but unreachable.

## 5. Quirks preserved for parity

* **Q1 — `OUT-ACCT-CURR-CYC-DEBIT` carry-over.** The field is only MOVEd when the input debit is zero,
  so otherwise the record area keeps what it held. Under GnuCOBOL the FD record area starts as low-values
  and is not cleared by WRITE: accounts before the first zero-debit account get `X'00…00'` (value 0.00,
  no valid sign nibble), every later account gets 2525.00, whatever its own debit
  (fixture `cyc-debit-carryover`). On z/OS the content after a QSAM WRITE is not defined; parity follows GnuCOBOL.
  In the sample data every debit is zero, so every row has 2525.00.
* **Q2 — reissue date reformat without validation** (BR-05): `20250520` → `202552`, `25-05-2020` → `25-0-220`,
  blank → blank (fixture `reissue-date-formats`). `VB2-ACCT-REISSUE-YYYY` uses the raw input bytes 1–4.
* **Q3 — group id blank in the sample data.** In `app/data/ASCII/acctdata.txt` the `A000000000` value sits in
  the `ACCT-ADDR-ZIP` columns, so `ACCT-GROUP-ID` / `out_acct_group_id` are blank. Parity follows the bytes.
* **Q4 — constants in the array record**: occurrences 1–3 carry the literal debits/balances of BR-08;
  occurrences 4–5 are always zero.
* **Q5 — `ERROR OPENING OUTFILE30`**: the status is concatenated without a separator; reproduced.
* **Q6 — "ACCOUNT FILE WRITE STATUS IS:"** is used for all three output files; reproduced.
* SYSOUT numerics: z/OS DISPLAYs zoned items as stored (`00000001940{`); the Java job prints the stored bytes.
  GnuCOBOL prints `000000019400+`; the fixture converter rewrites those lines to the z/OS form.
* Not reproduced: the packed (COMP-3) and variable-record (RDW) encodings, `ARR-FILLER`, and the
  `PREDEL` step (each run writes a new `run_id` partition). Not reproducible: bytes 14–38 of
  `CODATECN-ERROR-MSG` (the assembler `MVC COERMSG,=C'INVALID INPUT'` copies 38 bytes from a 13-byte literal;
  Java pads with spaces) and the implicit 20-byte `CLC COINPDT+4,=C'-'` (ported as the intended
  one-byte dash check). Neither is reachable from CBACT01C.

## 6. Traceability

Tests are in `jobs/cbact01c/src/test/java/com/carddemo/batch/cbact01c/`.

| Rule | Java (`Cbact01cJob.Execution` unless noted) | Tests |
|------|------|-------|
| BR-CBACT01C-01 | `procedureDivision`, `acctfileOpen` (`KeyedFile.inKeyOrder`), `acctfileGetNext` | `Cbact01cParityTest.sampleData`, `edgeCase[field-values-and-key-order]` |
| BR-CBACT01C-02 | `acctfileGetNext` | `sampleData`, `edgeCase[empty-acctfile]`, `abend[acctfile-read-error]` |
| BR-CBACT01C-03 | `displayAcctRecord` | `sampleData` (sysout), all `edgeCase` |
| BR-CBACT01C-04 | `populAcctRecord` | `sampleData`, `edgeCase[field-values-and-key-order]` |
| BR-CBACT01C-05 | `populAcctRecord`, `Cobdatft.call`, `Codatecn` | `edgeCase[reissue-date-formats]`, `CobdatftTest` |
| BR-CBACT01C-06 | `writeAcctRecord` | `sampleData`, all `edgeCase` |
| BR-CBACT01C-07 | `populAcctRecord` (`outAcctCurrCycDebit`) | `edgeCase[cyc-debit-carryover]`, `sampleData` |
| BR-CBACT01C-08 | `initializeArrArrayRec`, `populArrayRecord`, `writeArryRecord` | `sampleData`, all `edgeCase` |
| BR-CBACT01C-09 | `populVbrcRecord` | `sampleData`, `edgeCase[reissue-date-formats]` |
| BR-CBACT01C-10 | `writeVb1Record`, `writeVb2Record` | `sampleData`, all `edgeCase` |
| BR-CBACT01C-11 | `procedureDivision`, `acctfileClose`, `display` | `sampleData`, `edgeCase[empty-acctfile]` (return code, counters, sysout) |
| BR-CBACT01C-12 | `acctfileOpen`, `outfileOpen`, `arrfileOpen`, `vbrfileOpen`, `openOutput`, `acctfileGetNext`, `abendProgram` | `abend[acctfile-missing, outfile-open-error, arryfile-open-error, vbrcfile-open-error, acctfile-read-error]` |
| BR-CBACT01C-13 | `displayIoStatus`, `Cbact01cJob.ioStatus04` | `Cbact01cJobTest.displayIoStatus`, all `abend` cases |
| Schemas | `Cbact01cSchemas` | `Cbact01cJobTest.schemaContractsMatchCheckedInFiles`, published `_schema.json` checks in `Cbact01cParityTest` |
