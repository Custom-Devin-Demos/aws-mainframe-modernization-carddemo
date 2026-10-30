# CBACT02C GnuCOBOL parity harness

Runs the **original** `app/cbl/CBACT02C.cbl` (unmodified, with `app/cpy`) under GnuCOBOL 3.1.2 with BDB
indexed files and turns its SYSOUT into the canonical expected fixtures under
`src/test/resources/fixtures/`. The Java job is never used to produce expected output.

```bash
sudo apt-get install -y gnucobol3      # 3.1.2, BDB indexed files
jobs/cbact02c/tools/cobol/run.sh       # regenerates fixtures/*/expected (except the hand-derived ones)
git diff --stat src/test/resources/fixtures   # must be empty on a clean tree
```

| File | Role |
|------|------|
| `run.sh` | Compiles everything into `target/cobol/`, loads each scenario's unload into an indexed `CARDFILE`, runs CBACT02C, converts the output. |
| `LOADCARD.cbl` | Loader (stand-in for IDCAMS REPRO): reads the ASCII unload line-sequentially, pads/truncates to 150 bytes, drops CR and blank lines, WRITEs a KSDS keyed on bytes 1-16 (`KEYS(16 0)`, `RECORDSIZE(150 150)` as in `app/jcl/CARDFILE.jcl`). |
| `CEE3ABD.cbl` | Shim for the LE abend service: prints `ABCODE`/`TIMING` to SYSERR and ends with RC 99 (the Java launcher's abend exit). |
| `IOSTAT.cbl` | Driver for `9910-DISPLAY-IO-STATUS`: `run.sh` extracts the paragraph and its WORKING-STORAGE items verbatim from CBACT02C.cbl and the driver PERFORMs it for 21 file statuses. |
| `ebcdic_io_status.py` | Re-derives the 9910 table for the EBCDIC code page (cp037): the paragraph prints the *code point* of `IO-STAT2` for non-numeric / `9x` statuses, which differs between ASCII (GnuCOBOL) and z/OS. |
| `to_canonical.py` | Captured DISPLAY lines → `sysout.csv` (`line_no,line_text`, right-trimmed), `card_listing.csv` (CVACT02Y columns, PIC X right-trimmed, PIC 9 raw) or, for abended runs, `sysout.txt`. |

Compile flags: `-fsign=EBCDIC` (no signed fields in CVACT02Y, kept for consistency with the other jobs). No
`COBDATFT` shim is needed: CBACT02C does no date handling.
