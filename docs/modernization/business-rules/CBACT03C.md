# CBACT03C — Card cross-reference listing (READXREF)

Java job: `com.carddemo.batch.cbact03c.Cbact03cJob` (module `app/java/carddemo-batch/jobs/cbact03c`).
COBOL source: `app/cbl/CBACT03C.cbl`, copybook `app/cpy/CVACT03Y.cpy`, JCL `app/jcl/READXREF.jcl`.

## 1. Purpose and JCL context

CBACT03C opens the card→account cross-reference KSDS, browses it sequentially from the first key to the
end and DISPLAYs every `CARD-XREF-RECORD`. It has no other output; it is a diagnostic/listing program.

| JCL job | Step | Program | DD | Dataset |
|---|---|---|---|---|
| `READXREF` | `STEP05` | `CBACT03C` | `XREFFILE` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` (DISP=SHR) |
| | | | `SYSOUT`, `SYSPRINT` | `SYSOUT=*` |

* Upstream: the KSDS is defined and loaded from the flat unload by `app/jcl/XREFFILE.jcl` (IDCAMS). In the
  Java world the job reads that unload directly (`app/data/ASCII/cardxref.txt` format).
* Downstream: none. On AWS the job is the single state `STEP05` of the `READXREF` state machine
  (`jobs/cbact03c/deploy/READXREF.asl.json`), job definition `carddemo-cbact03c`
  (`jobs/cbact03c/deploy/cbact03c-job-definition.json`).

## 2. Inputs and outputs

| COBOL file | Copybook | Organization / access | Java |
|---|---|---|---|
| `XREFFILE` (FD `FD-XREFFILE-REC`: key `FD-XREF-CARD-NUM` X(16) + `FD-XREF-DATA` X(34)) | `CVACT03Y` `CARD-XREF-RECORD`, 50 bytes | KSDS, `ACCESS MODE IS SEQUENTIAL`, `RECORD KEY FD-XREF-CARD-NUM`, OPEN INPUT | `ctx.input("XREFFILE")`: fixed-width ASCII unload, loaded with `KeyedFile.load(..., "FD-XREF-CARD-NUM")` (`KsdsXrefFile`) |
| SYSOUT (DISPLAY) | — | — | `ctx.sysout()` and dataset `sysout` (CSV) |

Output datasets (design §5), schemas checked in under `src/main/resources/schemas/`:

| Dataset | Format | Columns | Content |
|---|---|---|---|
| `xref_listing` | Parquet | `xref_card_num` X(16), `xref_cust_id` 9(9), `xref_acct_id` 9(11) — all strings, from `Copybooks.CARD_XREF_RECORD` (FILLER dropped) | one row per record READ, in KSDS key order |
| `sysout` | CSV | `line_no` int (1-based), `line_text` string (right-trimmed) | every DISPLAY line, START/END included |

Counters in `JobResult`: `xref_records_read` (successful READs), `sysout_lines` (DISPLAY lines).

## 3. Processing rules

| ID | Paragraph | Rule |
|---|---|---|
| BR-CBACT03C-01 | PROCEDURE DIVISION (main) | DISPLAY `START OF EXECUTION OF PROGRAM CBACT03C` before anything else, then PERFORM `0000-XREFFILE-OPEN`. |
| BR-CBACT03C-02 | `0000-XREFFILE-OPEN` | `MOVE 8 TO APPL-RESULT`, OPEN INPUT `XREFFILE`; status `00` → `APPL-RESULT = 0` (continue); any other status → `APPL-RESULT = 12` → error path (§4). |
| BR-CBACT03C-03 | `1000-XREFFILE-GET-NEXT` | `READ XREFFILE INTO CARD-XREF-RECORD` returns the records in ascending key (`FD-XREF-CARD-NUM`) order, whatever the order of the unload. |
| BR-CBACT03C-04 | `1000-XREFFILE-GET-NEXT` + main loop | Status `00`: `APPL-RESULT = 0`, `DISPLAY CARD-XREF-RECORD` inside the paragraph; back in the main loop `IF END-OF-FILE = 'N'` displays the same record a second time. Every record therefore appears twice, consecutively. |
| BR-CBACT03C-05 | `1000-XREFFILE-GET-NEXT` | Status `10`: `APPL-RESULT = 16` (`APPL-EOF`) → `MOVE 'Y' TO END-OF-FILE`; nothing is displayed and the `PERFORM UNTIL END-OF-FILE = 'Y'` loop ends. An empty KSDS gives only the START and END lines. |
| BR-CBACT03C-06 | `1000-XREFFILE-GET-NEXT` | Any other read status: `APPL-RESULT = 12` → DISPLAY `ERROR READING XREFFILE` → error path (§4). |
| BR-CBACT03C-07 | `9000-XREFFILE-CLOSE` | CLOSE `XREFFILE`; `00` → continue; other → DISPLAY `ERROR CLOSING XREFFILE` → error path (§4). |
| BR-CBACT03C-08 | PROCEDURE DIVISION (main) | DISPLAY `END OF EXECUTION OF PROGRAM CBACT03C`, `GOBACK` with RETURN-CODE 0 (never set by the program). |
| BR-CBACT03C-09 | `9910-DISPLAY-IO-STATUS` | Line `FILE STATUS IS: NNNNxxxx`. Status not numeric or first byte `9`: `NNNN` + first byte + the binary value of the second byte as 3 digits (`9A` → `NNNN9065`, `92` → `NNNN9050`, `0M` → `NNNN0077`, `3X` → `NNNN3088`). Otherwise `NNNN00` + the two status bytes (`35` → `NNNN0035`). |
| BR-CBACT03C-10 | `9999-ABEND-PROGRAM` | DISPLAY `ABENDING PROGRAM`, `MOVE 999 TO ABCODE`, `CALL 'CEE3ABD'` → `AbendException(999)`; END is never displayed. |
| BR-CBACT03C-11 | `1000-XREFFILE-GET-NEXT` | The record is displayed as the raw 50-byte image: `XREF-CUST-ID`/`XREF-ACCT-ID` (PIC 9) are not validated and the 14-byte FILLER is shown. `xref_listing` carries the three named fields as stored (no numeric conversion), so non-digit content and short records (space-padded) are preserved. |

## 4. Error handling

| Point | Condition | SYSOUT | Result |
|---|---|---|---|
| `0000-XREFFILE-OPEN` | status ≠ `00` (missing DD or file → `35`; unloadable unload → `30`, see §5) | `ERROR OPENING XREFFILE`, `FILE STATUS IS: NNNNxxxx`, `ABENDING PROGRAM` | abend U999 |
| `1000-XREFFILE-GET-NEXT` | status ∉ {`00`,`10`} | `ERROR READING XREFFILE`, status line, `ABENDING PROGRAM` | abend U999 |
| `9000-XREFFILE-CLOSE` | status ≠ `00` | `ERROR CLOSING XREFFILE`, status line, `ABENDING PROGRAM` | abend U999 |

`9999-ABEND-PROGRAM` is the only abend point. The Java job throws `AbendException(999)`; `JobRunner` then
publishes no dataset and no manifest, and `BatchLauncher` exits with 99 (Step Functions state `Abended`).
There is no non-zero normal return code: success is always RC 0.

## 5. Quirks preserved for parity

* **Double DISPLAY** (BR-04): the record is displayed both in `1000-XREFFILE-GET-NEXT` and in the main loop
  (`CBACT03C.cbl` lines 96 and 78). Reproduced: `sysout` has `2 + 2 × records` lines.
* **Key order, not file order** (BR-03): a KSDS browse returns ascending keys. The shipped
  `app/data/ASCII/cardxref.txt` (50 records) happens to be in key order already, so the `unsorted-keys`
  fixture (6 records in scrambled order, loaded into a real BDB indexed file by GnuCOBOL) proves the sort.
  Collation is ASCII (GnuCOBOL and Java `String` order: digits < `A` < `a`). On z/OS the collation is
  EBCDIC (lower case < upper case < digits); for the real data (all-digit card numbers) both agree.
* **`9x` status rendering** (BR-09): the second status byte is moved into the low byte of a binary
  halfword and displayed as a number, so it shows the byte's code point. We reproduce the ASCII code
  point (GnuCOBOL golden: `92` → `NNNN9050`, `9A` → `NNNN9065`); on z/OS (EBCDIC) the same statuses would read
  `NNNN9242` and `NNNN9193`.
* **No validation** (BR-11): PIC 9 fields with letters, punctuation or spaces are listed unchanged.
* **Unload format**: CRLF and LF line ends, blank/whitespace-only lines skipped and short lines space-padded
  to 50 bytes, as the `XREFLOAD` GnuCOBOL loader and `FixedWidthFile` both do.
* **Not reproduced — duplicate keys and over-long records**: a KSDS cannot hold two records with the same
  key or a record longer than its 50-byte max (IDCAMS REPRO rejects them before CBACT03C runs). The Java
  job reads the unload directly, so it reports such an unload as an OPEN failure with status `30`
  (`ERROR OPENING XREFFILE`, `NNNN0030`, abend U999) instead of silently picking one record.
* **No outputs on abend**: the COBOL SYSOUT up to the abend goes to CloudWatch via `ctx.sysout()`, but
  per the design no `sysout`/`xref_listing` dataset is published for an abended run.

## 6. Traceability

| Rule | Java | Tests |
|---|---|---|
| BR-CBACT03C-01 | `Cbact03cJob.Execution#procedureDivision` | `Cbact03cParityTest#sampleData`, `#edgeCaseCompletes`, `#openErrorAbends` |
| BR-CBACT03C-02 | `Execution#xreffileOpen`, `KsdsXrefFile#open` | `Cbact03cParityTest#openErrorAbends` (missing file, missing DD, duplicate key, over-long record) |
| BR-CBACT03C-03 | `KsdsXrefFile#open`/`#read` (`KeyedFile` key order) | `Cbact03cParityTest#unsortedUnloadIsListedInAsciiKeyOrder`, `#edgeCaseCompletes[unsorted-keys]` |
| BR-CBACT03C-04 | `Execution#xreffileGetNext`, `#procedureDivision` | `Cbact03cParityTest#sampleData` (counts 50 / 102), all completing scenarios |
| BR-CBACT03C-05 | `Execution#xreffileGetNext` | `Cbact03cParityTest#emptyKsdsDisplaysOnlyStartAndEnd`, `#edgeCaseCompletes[empty-file]` |
| BR-CBACT03C-06 | `Execution#xreffileGetNext` | `Cbact03cParityTest#injectedIoErrorAbends[read-error]` |
| BR-CBACT03C-07 | `Execution#xreffileClose`, `KsdsXrefFile#close` | `Cbact03cParityTest#injectedIoErrorAbends[close-error]` |
| BR-CBACT03C-08 | `Execution#procedureDivision` (`JobResult.ok`) | `Cbact03cParityTest#sampleData`, `Cbact03cJobTest#launcherFindsJobAndRunsIt` |
| BR-CBACT03C-09 | `Cbact03cJob#ioStatusLine`, `Execution#displayIoStatus` | `Cbact03cJobTest#displayIoStatusMatchesCobolParagraph` (17 statuses) |
| BR-CBACT03C-10 | `Execution#abendProgram` | `Cbact03cParityTest#openErrorAbends`, `#injectedIoErrorAbends`, `Cbact03cJobTest#launcherExitsWithAbendStatusWhenXreffileIsMissing` |
| BR-CBACT03C-11 | `Execution#readInto`, `#display` | `Cbact03cParityTest#displayShowsFillerAndUnvalidatedContent`, `#edgeCaseCompletes[raw-content, unload-format]` |
| Schemas | `Cbact03cJob#xrefListingSchema`, `#sysoutSchema` | `Cbact03cJobTest#schemaContracts`, `#publishedSchemasEqualContracts` |
