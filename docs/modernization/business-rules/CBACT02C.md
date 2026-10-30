# CBACT02C — Card file read

Source: `app/cbl/CBACT02C.cbl` (copybook `app/cpy/CVACT02Y.cpy`). Java: `app/java/carddemo-batch/jobs/cbact02c`,
package `com.carddemo.batch.cbact02c`, main class `Cbact02cJob`.

## 1. Purpose and JCL context

CBACT02C is a utility that lists the card master file: it browses the card KSDS sequentially from the first
key to the last and DISPLAYs every 150-byte `CARD-RECORD` on SYSOUT. It changes no data.

| JCL | Step | DD | Dataset |
|-----|------|----|---------|
| `app/jcl/READCARD.jcl` | `STEP05 EXEC PGM=CBACT02C` | `CARDFILE` | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` (DISP=SHR) |
| | | `SYSOUT`, `SYSPRINT` | `SYSOUT=*` |

Upstream: `CARDFILE.jcl` defines the KSDS (`KEYS(16 0)`, `RECORDSIZE(150 150)`) and REPROs
`app/data/ASCII/carddata.txt` into it. Downstream: none (READCARD is a standalone listing job).
On AWS the step is `deploy/READCARD.asl.json` → AWS Batch job `carddemo-cbact02c`.

## 2. Inputs and outputs

| COBOL file | Copybook | Organization / access | Java |
|------------|----------|-----------------------|------|
| `CARDFILE-FILE` (DD `CARDFILE`) | FD `FD-CARDFILE-REC` (`FD-CARD-NUM` X(16) key + X(134)); `READ ... INTO CARD-RECORD` (CVACT02Y) | KSDS, `ACCESS MODE IS SEQUENTIAL`, `OPEN INPUT` | `ctx.optionalInput("CARDFILE")`: the legacy fixed-width ASCII unload, loaded with `KeyedFile.load(..., "FD-CARD-NUM")` and browsed with `inKeyOrder()` |
| SYSOUT (DISPLAY) | — | — | `JobContext.sysout()` (stdout / CloudWatch) and dataset `sysout` |

| Output dataset | Format | Columns | Content |
|----------------|--------|---------|---------|
| `card_listing` | Parquet | `card_num`, `card_acct_id`, `card_cvv_cd`, `card_embossed_name`, `card_expiraion_date`, `card_active_status` (all `string`, from `Copybooks.CARD_RECORD`; FILLER dropped) | one row per DISPLAYed card, in key order |
| `sysout` | CSV | `line_no` (int, 1-based), `line_text` (string, right-trimmed) | every DISPLAY line in order, START and END included |

Both are published only when the program ends normally; schema contracts are in
`jobs/cbact02c/src/main/resources/schemas/`. Counters (`JobResult`, manifest): `card_records_read`, `sysout_lines`.

## 3. Processing rules

- **BR-CBACT02C-01** (PROCEDURE DIVISION main line): first DISPLAY `START OF EXECUTION OF PROGRAM CBACT02C`, before the OPEN (so it is also the first line of an abended run).
- **BR-CBACT02C-02** (`0000-CARDFILE-OPEN`): `OPEN INPUT CARDFILE-FILE`; status `00` → `APPL-RESULT` 0, any other status → 12 → error path (§4). Java statuses: `35` DD not allocated or file absent; `39` unload not loadable as this KSDS (record longer than 150 bytes, duplicate key, non-ASCII bytes); `30` I/O error reading the unload.
- **BR-CBACT02C-03** (`1000-CARDFILE-GET-NEXT`, FD `RECORD KEY IS FD-CARD-NUM`): each READ returns the next record in ascending primary-key order (KSDS browse), regardless of the order of the unload. Keys compare as raw 16-byte strings; a short key is space-padded (`4111` sorts after `2760…` and before `9999…`). The sample unload happens to be sorted already (asserted by a test); `key-order` uses an unsorted one.
- **BR-CBACT02C-04** (main line, `DISPLAY CARD-RECORD`): after every successful READ (`END-OF-FILE = 'N'`) the full 150-byte `CARD-RECORD` is DISPLAYed, FILLER included, no field formatting. The same record becomes one `card_listing` row: PIC X fields right-trimmed, PIC 9 fields (`CARD-ACCT-ID`, `CARD-CVV-CD`) kept as their raw characters (no numeric validation, so blank or non-numeric bytes pass through).
- **BR-CBACT02C-05** (`1000-CARDFILE-GET-NEXT`): only status `00` is a successful READ (`APPL-AOK`); `10` sets `APPL-EOF` → `END-OF-FILE = 'Y'` and ends the loop without DISPLAY; any other status (including the "successful" `04`) → 12 → error path.
- **BR-CBACT02C-06** (main line + `1000-CARDFILE-GET-NEXT`): an empty file returns `10` on the first READ: output is START + END only, `card_listing` has zero rows, RC 0.
- **BR-CBACT02C-07** (`9000-CARDFILE-CLOSE`): after EOF, `CLOSE CARDFILE-FILE`; status `00` → continue, else → error path.
- **BR-CBACT02C-08** (main line, `GOBACK`): DISPLAY `END OF EXECUTION OF PROGRAM CBACT02C` and return with RETURN-CODE 0 (never set by the program) → `JobResult.ok`, exit 0.
- **BR-CBACT02C-09** (`9910-DISPLAY-IO-STATUS`): DISPLAY `FILE STATUS IS: NNNN` + `IO-STATUS-04`: for a numeric status not starting with `9`, `00` + status (`35` → `0035`); otherwise `IO-STAT1` + the code point of `IO-STAT2` as 3 digits (via `TWO-BYTES-BINARY`), using EBCDIC as on z/OS (`93` → `9243`, `0A` → `0193`).
- **BR-CBACT02C-10** (`9999-ABEND-PROGRAM`): DISPLAY `ABENDING PROGRAM`, then `CALL 'CEE3ABD'` with `ABCODE` 999, `TIMING` 0 → `AbendException(999)`; the launcher exits 99 and the Step Functions state goes to `Abended`.
- **BR-CBACT02C-11** (ingestion, replaces IDCAMS REPRO in `CARDFILE.jcl`): the unload is one record per line; LF or CRLF line ends, lines shorter than 150 bytes are space-padded (trailing blanks may be stripped), blank lines and a missing final newline are ignored (`FixedWidthFile` rules).

## 4. Error handling

| Point | Condition | SYSOUT (after START and any cards already listed) | Result |
|-------|-----------|------|--------|
| `0000-CARDFILE-OPEN` | status ≠ `00` | `ERROR OPENING CARDFILE`, `FILE STATUS IS: NNNNxxxx`, `ABENDING PROGRAM` | U999 abend |
| `1000-CARDFILE-GET-NEXT` | status ∉ {`00`,`10`} | `ERROR READING CARDFILE`, status line, `ABENDING PROGRAM` | U999 abend |
| `9000-CARDFILE-CLOSE` | status ≠ `00` | `ERROR CLOSING CARDFILE`, status line, `ABENDING PROGRAM` | U999 abend (END line never shown) |

No INVALID KEY / AT END clauses: all branching is on `CARDFILE-STATUS`. There is no RC 4/8 path. On abend no
dataset, schema or manifest is published; SYSOUT so far goes to the stream (CloudWatch). The `AbendException`
message carries the operation, file status and the cause (e.g. `no such file ...`, `duplicate key ...`).

## 5. Quirks preserved for parity

- READ status `04` (and any non-`00`, non-`10` status) abends, although `04` is a successful read in COBOL (BR-05; `1000-CARDFILE-GET-NEXT` tests `= '00'` only).
- `9910-DISPLAY-IO-STATUS` prints a 4-digit "status" that is really a code-page byte value for `9x`/non-numeric statuses; reproduced with EBCDIC so the text matches the mainframe, not GnuCOBOL (`ascii.csv` vs `ebcdic.csv` fixtures).
- `DISPLAY CARD-RECORD` prints the FILLER bytes (positions 92-150) even though they are not part of any field; the stream keeps them (150 characters per card line), the `sysout` dataset right-trims, `card_listing` drops FILLER.
- PIC 9 fields are never validated: blanks / letters in `CARD-ACCT-ID` or `CARD-CVV-CD` are listed as-is (`raw-content` fixture). Hence all `card_listing` columns are `string`.
- The copybook spelling `CARD-EXPIRAION-DATE` is kept as column `card_expiraion_date` (design §5 naming rule).
- `APPL-RESULT` is set to 8 before OPEN/CLOSE and then always overwritten; kept as written, no effect.

Not reproduced:

- Collation: a z/OS KSDS orders keys in EBCDIC, Java in UTF-16/ASCII order. They differ only between letters and digits or between upper and lower case; card numbers are all digits (and spaces for short keys, which sort first in both), so the order is the same. Documented, not emulated.
- The mainframe RC 0 job log / `SYSPRINT` (empty for this program) are not produced.
- Malformed unloads (record > 150 bytes, duplicate keys, non-ASCII) cannot reach the program on z/OS: REPRO would reject them. The Java job reports them as OPEN status `39`, an assumption documented with the hand-derived fixtures.

## 6. Traceability

| Rule | Java (`com.carddemo.batch.cbact02c`) | Tests |
|------|--------------------------------------|-------|
| BR-CBACT02C-01 | `Cbact02cJob.Execution#procedureDivision` | `SampleDataParityTest.sysoutStreamHasStartEndAndFullRecordImages`, `.sysoutDatasetMatchesCobol`; `AbendParityTest.*` |
| BR-CBACT02C-02 | `Execution#cardfileOpen`, `KsdsCardFile#open` | `AbendParityTest.missingFileAbendsWithStatus35`, `.missingDdAbendsWithStatus35`, `.malformedUnloadAbendsWithStatus39` (3 cases), `.unreadableFileAbendsWithStatus30`; `FileStatusBranchesTest.openStatus97IsTreatedAsAnError` |
| BR-CBACT02C-03 | `Execution#cardfileGetNext`, `KsdsCardFile#readNext` (`KeyedFile.inKeyOrder`) | `EdgeCaseParityTest.outputsMatchCobol[key-order]`, `.keyOrderInputIsNotSortedButOutputIs`; `SampleDataParityTest.sampleUnloadIsAlreadyInKeyOrder`, `.cardListingMatchesCobol` |
| BR-CBACT02C-04 | `Execution#displayCardRecord`, `DisplayLog#display`, `Execution#publishOutputs` | `SampleDataParityTest.cardListingMatchesCobol`; `EdgeCaseParityTest.outputsMatchCobol[raw-content]`, `.displayShowsFillerBytesThatTheDatasetDrops`; `SchemaContractTest.cardListingContract` |
| BR-CBACT02C-05 | `Execution#cardfileGetNext` | `FileStatusBranchesTest.readErrorAfterTwoRecordsAbends` (30, 04, 23, 93); all completed scenarios (status 10) |
| BR-CBACT02C-06 | `Execution#procedureDivision`, `#cardfileGetNext` | `EdgeCaseParityTest.outputsMatchCobol[empty-file]`, `.emptyFileDisplaysOnlyStartAndEnd` |
| BR-CBACT02C-07 | `Execution#cardfileClose`, `KsdsCardFile#close` | `FileStatusBranchesTest.closeErrorAbendsAfterAllRecordsAndBeforeEndMessage`, `.ksdsReadAndCloseBeforeOpenReturnNotOpenStatuses` |
| BR-CBACT02C-08 | `Execution#procedureDivision` (`JobResult.ok`) | `SampleDataParityTest.returnCodeAndCounters`, `.manifestRecordsReturnCodeCountersAndOutputs`; `LauncherTest.sampleRunExitsZeroAndPublishesCuratedLayout` |
| BR-CBACT02C-09 | `Execution#displayIoStatus`, `IoStatus#ioStatus04` | `IoStatusTest.matchesGnuCobolUnderAsciiCodePage` (21), `.jobUsesEbcdicCodePageLikeZos` (21), `.rejectsStatusThatIsNotTwoCharacters` |
| BR-CBACT02C-10 | `Execution#abendProgram` | `AbendParityTest.*`, `FileStatusBranchesTest.*` (no files published); `LauncherTest.abendExits99AndPublishesNothing` |
| BR-CBACT02C-11 | `KsdsCardFile#open` (`KeyedFile` / `FixedWidthFile`) | `EdgeCaseParityTest.outputsMatchCobol[unload-format]` |

Deploy templates: `DeployTemplatesTest`. Sysout contract: `SchemaContractTest.sysoutContract`,
`SampleDataParityTest.publishedSchemasEqualCheckedInContracts`.
