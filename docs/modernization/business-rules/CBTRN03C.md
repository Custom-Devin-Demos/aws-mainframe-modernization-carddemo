# CBTRN03C — Transaction detail report

## 1. Purpose and JCL context

`CBTRN03C` prints the daily transaction detail report: every posted transaction in a processing-date
range, grouped by card, with account, page and grand totals (CVTRA07Y layouts, 133-byte lines).

JCL `app/jcl/TRANREPT.jcl`:

1. `STEP05R` (`REPROC`): IDCAMS REPRO of the TRANSACT KSDS to `TRANSACT.BKUP(+1)`.
2. `STEP05R` (`SORT`): `SORT FIELDS=(TRAN-CARD-NUM,A)` (`263,16,ZD`) with
   `INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,TRAN-PROC-DT,LE,PARM-END-DATE)` (`305,10,CH`,
   SYMNAMES defaults `2022-01-01` / `2022-07-06`) to `TRANSACT.DALY(+1)`.
3. `STEP10R`: `CBTRN03C` reading `TRANSACT.DALY(+1)` as TRANFILE.

In Java (design §2.2) steps 1 and 2 are folded into the job as `TransactSortStep`, so the job's TRANFILE DD
is the TRANSACT KSDS unload itself.

## 2. Inputs and outputs

| DD | Layout | Access | Java |
| --- | --- | --- | --- |
| TRANFILE | CVTRA05Y (350) TRANSACT unload | REPRO + SORT, then sequential | `ctx.optionalInput("TRANFILE")`, `TransactSortStep` |
| CARDXREF | CVACT03Y (50), key XREF-CARD-NUM | random | `KeyedFile` |
| TRANTYPE | CVTRA03Y (60), key TRAN-TYPE | random | `KeyedFile` |
| TRANCATG | CVTRA04Y (60), key TRAN-TYPE-CD + TRAN-CAT-CD | random | `KeyedFile` |
| DATEPARM | `start-date(10) filler(1) end-date(10)` in an 80-byte record | sequential, first record | `FixedWidthFile`, or `--param START-DATE=` / `END-DATE=` when the DD is absent |
| TRANREPT | 133-byte lines (CVTRA07Y) | output | dataset `transaction_report` (CSV), one row per line |

Parameters: `START-DATE`, `END-DATE` (used only without a DATEPARM DD; the DD wins when both are given);
`SORT-START-DATE`, `SORT-END-DATE` (the SORT SYMNAMES `PARM-START-DATE` / `PARM-END-DATE`; default = the
DATEPARM dates, else `2022-01-01` / `2022-07-06` when DATEPARM is empty or missing). In TRANREPT the SYMNAMES
dates and the DATEPARM file are maintained separately; defaulting one to the other keeps them in step, and the
separate parameters still allow reproducing a mismatch (BR-CBTRN03C-07).

`transaction_report` columns: `line_no`, `page_no`, `line_type` (`HEADER`, `BLANK`, `COLUMN_HEADER`, `RULE`,
`DETAIL`, `PAGE_TOTAL`, `ACCOUNT_TOTAL`, `GRAND_TOTAL`), nullable detail fields `tran_id`, `account_id`,
`tran_type_cd`, `tran_type_desc`, `tran_cat_cd`, `tran_cat_desc`, `tran_source` (as printed, right-trimmed),
`amount` (decimal(11,2): TRAN-AMT for details, the printed total for totals, null otherwise) and `line_text`
(the 133-byte line, right-trimmed). Contract: `jobs/cbtrn03c/src/main/resources/schemas/transaction_report.schema.json`.

Counters: `transact_unloaded`, `sort_included`, `sort_omitted`, `tranfile_read`, `detail_lines`,
`account_totals`, `page_totals`, `pages`, `report_lines`.

## 3. Processing rules

* **BR-CBTRN03C-01** (TRANREPT STEP05R REPRO/SORT): the TRANSACT unload is taken in TRAN-ID key order (REPRO of
  the KSDS; duplicate keys are rejected as a KSDS would), records whose `TRAN-PROC-TS(1:10)` is between the SORT
  dates inclusive are kept (character comparison, the time part is ignored, a blank timestamp is excluded), and
  they are sorted ascending by TRAN-CARD-NUM as zoned decimal.
* **BR-CBTRN03C-02** (STEP05R SORT, stability): DFSORT's default (EQUALS=NO) does not guarantee the order of
  records with the same card number. The migration fixes it to EQUALS: within a card, records stay in TRAN-ID
  order. This only affects detail order within a card (and therefore which page a line lands on), not totals.
* **BR-CBTRN03C-03** (`0000-TRANFILE-OPEN` … `0500-DATEPARM-OPEN`): files are opened in the order TRANFILE,
  TRANREPT, CARDXREF, TRANTYPE, TRANCATG, DATEPARM; an unallocated DD fails the OPEN.
* **BR-CBTRN03C-04** (`0550-DATEPARM-READ`): the first DATEPARM record gives WS-START-DATE (1-10) and WS-END-DATE
  (12-21) and `Reporting from <start> to <end>` is displayed. An empty DATEPARM (status 10) sets END-OF-FILE: no
  transaction is read and no report line is written.
* **BR-CBTRN03C-05** (`0500-DATEPARM-OPEN`, Java choice): without a DATEPARM DD, `START-DATE` and `END-DATE`
  parameters build the same record (`start + ' ' + end`).
* **BR-CBTRN03C-06** (`1000-TRANFILE-GET-NEXT`): TRANFILE is read sequentially; at end of file TRAN-RECORD keeps
  the last record read (READ INTO does not move on EOF).
* **BR-CBTRN03C-07** (PROCEDURE DIVISION main loop): after each READ, `TRAN-PROC-TS(1:10)` is compared with
  WS-START-DATE/WS-END-DATE; out of range takes `NEXT SENTENCE`, which leaves the `PERFORM UNTIL` (see §5).
* **BR-CBTRN03C-08** (main loop): each record in range is DISPLAYed (350-byte image).
* **BR-CBTRN03C-09** (main loop, `1120-WRITE-ACCOUNT-TOTALS`, `1500-A-LOOKUP-XREF`): when TRAN-CARD-NUM differs
  from WS-CURR-CARD-NUM, the previous card's account total and a rule line are written (except before the first
  detail), the account total is reset, and CARDXREF is read by card number. The xref is only read on a card change.
* **BR-CBTRN03C-10** (`1500-B-LOOKUP-TRANTYPE`, `1500-C-LOOKUP-TRANCATG`): TRANTYPE (TRAN-TYPE-CD) and TRANCATG
  (TRAN-TYPE-CD + TRAN-CAT-CD) are read for every record.
* **BR-CBTRN03C-11** (`1100-WRITE-TRANSACTION-REPORT`, `1120-WRITE-HEADERS`): before the first detail the four
  header lines are written (report name with the DATEPARM dates, blank, column header, rule).
* **BR-CBTRN03C-12** (`1100-WRITE-TRANSACTION-REPORT`, `1110-WRITE-PAGE-TOTALS`): before each detail, if
  `MOD(WS-LINE-COUNTER, 20) = 0` the page total and a rule are written, the page total is added to the grand total
  and reset, and the headers are written again. Page breaks may fall inside an account.
* **BR-CBTRN03C-13** (`1120-WRITE-*`, `1110-WRITE-PAGE-TOTALS`): WS-LINE-COUNTER counts headers (4), details (1),
  page and account totals (2 each, total + rule); the grand total is not counted.
* **BR-CBTRN03C-14** (`1120-WRITE-DETAIL`): the detail line holds TRAN-ID, XREF-ACCT-ID, TRAN-TYPE-CD,
  TRAN-TYPE-DESC truncated to 15, TRAN-CAT-CD, TRAN-CAT-TYPE-DESC truncated to 29, TRAN-SOURCE and TRAN-AMT;
  TRAN-AMT is added to the page and account totals.
* **BR-CBTRN03C-15** (CVTRA07Y): amounts are printed with `-ZZZ,ZZZ,ZZZ.ZZ` (details) and `+ZZZ,ZZZ,ZZZ.ZZ`
  (totals): leading zeros and their commas are blanked up to the decimal point, the sign position shows `-`
  for negatives and space (`-` picture) or `+` (`+` picture) otherwise, and zero prints as 15 spaces.
* **BR-CBTRN03C-16** (`ADD … TO WS-PAGE-TOTAL WS-ACCOUNT-TOTAL`, `ADD WS-PAGE-TOTAL TO WS-GRAND-TOTAL`): the totals
  are `S9(09)V99` without ON SIZE ERROR, so high-order digits are lost on overflow.
* **BR-CBTRN03C-17** (main loop, EOF branch): at end of file the program displays `TRAN-AMT <zoned>` and
  `WS-PAGE-TOTAL<zoned>`, adds the last TRAN-AMT again to the page and account totals, writes the page total
  and the grand total. No account total is written for the last card (see §5).
* **BR-CBTRN03C-18** (`GOBACK`): after closing the files `END OF EXECUTION OF PROGRAM CBTRN03C` is displayed;
  RETURN-CODE is never set, so a completed run returns 0.

## 4. Error handling

* **BR-CBTRN03C-19** (`1500-A/B/C-LOOKUP-*`, `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM`): an INVALID KEY on
  CARDXREF / TRANTYPE / TRANCATG displays `INVALID CARD NUMBER : <card>`, `INVALID TRANSACTION TYPE : <type>` or
  `INVALID TRAN CATG KEY : <type><cat>`, then `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, and calls
  `CEE3ABD` with code 999. Java throws `AbendException(999)`; nothing is published and the launcher exits 99.
* **BR-CBTRN03C-20** (`0000`…`0500-*-OPEN`, `9910-DISPLAY-IO-STATUS`): an OPEN failure displays
  `ERROR OPENING TRANFILE` / `REPTFILE` / `CROSS REF FILE` / `TRANSACTION TYPE FILE` / `TRANSACTION CATG FILE` /
  `DATE PARM FILE`, the status (`FILE STATUS IS: NNNN0035` for a missing DD) and abends U999. In Java a missing
  DD (and, for DATEPARM, no `START-DATE`/`END-DATE` parameters) takes this path. 9910 prints a numeric status
  as `NNNN00xx`; a non-numeric or `9x` status as `NNNN9` + the binary value of the second byte (3 digits).
* Not reachable in Java (no equivalent I/O failure): READ errors other than EOF on TRANFILE/DATEPARM, TRANREPT
  WRITE errors, and CLOSE errors (`9000`…`9500-*-CLOSE`). They would abend U999 the same way.

## 5. Quirks preserved for parity

* **`NEXT SENTENCE` in the date filter** (BR-CBTRN03C-07): the `IF … CONTINUE ELSE NEXT SENTENCE END-IF` sits
  inside an inline `PERFORM UNTIL … END-PERFORM.`; NEXT SENTENCE jumps past the period, i.e. out of the loop.
  The first out-of-range record therefore ends the report: no further details, no page total, no grand
  total; the program still ends normally (RC 0). Verified under GnuCOBOL (`date-filter-next-sentence`). With the
  default SORT dates (= DATEPARM dates) every record is in range, so this only shows when the two disagree.
* **Empty TRANFILE prints nothing** (BR-CBTRN03C-06/07): on the first READ at EOF, TRAN-RECORD is still its
  initial value (spaces), the filter fails and NEXT SENTENCE exits before the EOF branch: no headers and no
  totals (`no-selected-records`).
* **Last amount counted twice at EOF** (BR-CBTRN03C-17): the final page total, and through it the grand total,
  include the last TRAN-AMT twice; the last card gets no account total (`eof-last-account`, sample data).
* **Page size 20 via `MOD(WS-LINE-COUNTER, WS-PAGE-SIZE)`** (BR-CBTRN03C-12/13): the check runs only before a
  detail, and total lines advance the counter by 2, so a page can run past 20 lines when an account total steps
  over a multiple of 20 (`pagination`: 39 lines on page 2).
* **Edited pictures** (BR-CBTRN03C-15): a zero detail amount prints as blanks; sub-unit values print as `.05`.
* **Total overflow** (BR-CBTRN03C-16): e.g. 2 × 999,999,999.99 − 1,234,567.89 prints as `+998,766,432.09`.
* **Descriptions truncated** to X(15) / X(29) in the detail line (BR-CBTRN03C-14).
* **DISPLAY of numeric items** shows the zoned image (`TRAN-AMT 0000007656F`), as z/OS does.

Deviations: DISPLAY of TRAN-RECORD re-encodes the record from its fields (`RecordLayout.format`), so FILLER
bytes print as spaces and a non-canonical sign overpunch would print in canonical form; the sample and fixture
data are canonical. Sort stability is fixed to EQUALS (BR-CBTRN03C-02).

## 6. Traceability

| Rule | Java | Test |
| --- | --- | --- |
| BR-CBTRN03C-01 | `TransactSortStep.run`, `repro`, `include`, `zonedKey` | `TransactSortStepTest` (all); `Cbtrn03cEdgeCaseParityTest.completedRunMatchesCobol[presort-filter-order]` |
| BR-CBTRN03C-02 | `TransactSortStep.run` (stable `List.sort`) | `TransactSortStepTest.sortsByCardNumberKeepingEqualKeysInReproOrder` |
| BR-CBTRN03C-03 | `Cbtrn03cJob.Run.tranfileOpen`, `reptfileOpen`, `cardxrefOpen`, `trantypeOpen`, `trancatgOpen`, `dateparmOpen` | `Cbtrn03cEdgeCaseParityTest.missingDateparmIsOpenErrorStatus35` |
| BR-CBTRN03C-04 | `Run.dateparmRead` | `Cbtrn03cSampleDataParityTest.sysoutMatchesCobol`; `completedRunMatchesCobol[empty-dateparm]` |
| BR-CBTRN03C-05 | `Run.dateParmRecords` | `completedRunMatchesCobol[dateparm-from-params]` |
| BR-CBTRN03C-06 | `Run.tranfileGetNext` | `completedRunMatchesCobol[no-selected-records]`, `emptyTranfileWritesNoReportLines` |
| BR-CBTRN03C-07 | `Run.procedureDivision` (date filter `break`) | `nextSentenceEndsTheLoopWithoutTotals`; `completedRunMatchesCobol[date-filter-next-sentence]` |
| BR-CBTRN03C-08 | `Run.procedureDivision` (`display(TRAN_RECORD.format(...))`) | `Cbtrn03cSampleDataParityTest.sysoutMatchesCobol` |
| BR-CBTRN03C-09 | `Run.procedureDivision`, `writeAccountTotals`, `lookupXref` | `Cbtrn03cSampleDataParityTest.transactionReportMatchesCobol`; `completedRunMatchesCobol[pagination]` |
| BR-CBTRN03C-10 | `Run.lookupTrantype`, `lookupTrancatg` | `Cbtrn03cSampleDataParityTest.transactionReportMatchesCobol` |
| BR-CBTRN03C-11 | `Run.writeTransactionReport`, `writeHeaders` | `Cbtrn03cSampleDataParityTest.printImageMatchesCobol` |
| BR-CBTRN03C-12 | `Run.writeTransactionReport`, `writePageTotals` | `pageBreaksWhenLineCounterIsAMultipleOf20`; `completedRunMatchesCobol[pagination]` |
| BR-CBTRN03C-13 | `Run.writeHeaders`, `writeDetail`, `writePageTotals`, `writeAccountTotals` | `pageBreaksWhenLineCounterIsAMultipleOf20` |
| BR-CBTRN03C-14 | `Run.writeDetail`, `ReportLines.detail` | `completedRunMatchesCobol[edited-amounts-overflow]`; `ReportLinesTest.lineLayoutsMatchCvtra07y` |
| BR-CBTRN03C-15 | `ReportLines.edit` | `ReportLinesTest.editedPictures` |
| BR-CBTRN03C-16 | `ReportLines.toPicture` | `ReportLinesTest.totalsLoseHighOrderDigits`; `completedRunMatchesCobol[edited-amounts-overflow]` |
| BR-CBTRN03C-17 | `Run.procedureDivision` (EOF branch), `writeGrandTotals` | `eofAddsLastAmountAgainAndSkipsLastAccountTotal`; `completedRunMatchesCobol[eof-last-account]` |
| BR-CBTRN03C-18 | `Run.procedureDivision` (`JobResult.ok`) | `Cbtrn03cSampleDataParityTest.returnCodeAndCounters` |
| BR-CBTRN03C-19 | `Run.lookupXref`, `lookupTrantype`, `lookupTrancatg`, `displayIoStatus`, `abendProgram` | `abendMatchesCobol[invalid-xref, invalid-trantype, invalid-trancatg]`, `invalidLookupsDisplayKeyAndStatus23` |
| BR-CBTRN03C-20 | `Run.openError`, `displayIoStatus`, `abendProgram` | `abendMatchesCobol[missing-dateparm-abend]` |
