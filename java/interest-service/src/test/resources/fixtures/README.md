# Fixtures (Child C)

Golden/fixture data for integration and parity tests of the CBACT04C reimplementation.

Expected layout (one directory per scenario):

```
fixtures/<scenario>/
  input/tcatbal.txt    CVTRA01Y records (50 bytes, ASCII, zoned-decimal overpunch sign)
  input/discgrp.txt    CVTRA02Y records (50 bytes)
  input/acctdata.txt   CVACT01Y records (300 bytes)
  input/cardxref.txt   CVACT03Y records (50 bytes)
  expected/systran.txt  CVTRA05Y records (350 bytes) written by CBACT04C
  expected/acctdata.txt CVACT01Y records after 1050-UPDATE-ACCOUNT
  README.md             how the expected output was produced (run date, tooling)
```

Source data lives in `app/data/ASCII/` (ASCII, one record per line) and `app/data/EBCDIC/`.
