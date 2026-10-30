# GnuCOBOL golden-output harness for CBACT03C

Regenerates the GnuCOBOL-derived expected fixtures in `src/test/resources/fixtures/*/expected/` from the
original `app/cbl/CBACT03C.cbl`. Nothing here is used by the Maven build.

```bash
sudo apt-get install -y gnucobol3     # GnuCOBOL 3.1.2, BDB indexed files
app/java/carddemo-batch/jobs/cbact03c/tools/cobol/run-cobol.sh
git diff app/java/carddemo-batch/jobs/cbact03c/src/test/resources/fixtures   # expect no change
```

Work files go to `jobs/cbact03c/target/cobol-harness/`.

| File | Role |
|---|---|
| `run-cobol.sh` | compiles everything, loads each scenario's unload, runs CBACT03C, calls `to_canonical.py` |
| `CEE3ABD.cbl` | stub for the LE abend service: prints `CEE3ABD ABCODE=<code>` to stderr and ends with status 99 |
| `XREFLOAD.cbl` | loads an ASCII unload (`DD_XREFIN`) into a BDB indexed file (`DD_XREFFILE`, key = bytes 1–16); strips CR, skips blank lines, pads short lines |
| `gen_iostatdrv.py` + `IOSTATDRV.cbl.template` | copy the original `9910-DISPLAY-IO-STATUS` paragraph and its working storage out of CBACT03C.cbl into a driver that runs it for each status in `display-io-status/input/statuses.txt` |
| `to_canonical.py` | raw SYSOUT/exit status → `sysout.txt`, `sysout.csv`, `xref_listing.csv`, `run.properties`; statuses → `display-io-status.csv` |

Shims: only `CEE3ABD`. CBACT03C calls no `COBDATFT` and has no signed fields; `-fsign=EBCDIC` is kept
for consistency with the other CardDemo harnesses. Files are mapped to DD names through GnuCOBOL's
`DD_<name>` environment variables.

Scenarios the unmodified program cannot reach under GnuCOBOL (read/close errors, duplicate keys,
over-long records, missing DD) are derived by hand; see `src/test/resources/fixtures/README.md`.
