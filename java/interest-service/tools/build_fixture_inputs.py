#!/usr/bin/env python3
"""Build the ``input/`` directories of every fixture scenario from app/data/ASCII.

Normalisation applied to every file (emulates what a VSAM KSDS gives CBACT04C):
  * CRLF -> LF, trailing whitespace preserved, each record padded with spaces to
    its copybook length (50 / 50 / 300 / 50);
  * records sorted ascending by primary key, byte-wise (LC_ALL=C order).

Scenario specific edits are expressed as small Python functions below so that the
"hand edits" are reproducible.  Signed COBOL fields are written with the
EBCDIC-style zoned-decimal overpunch used by the shipped ASCII data
({=+0 A..I=+1..+9, }=-0 J..R=-1..-9).
"""
from __future__ import annotations

import sys
from decimal import Decimal
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
SRC = REPO / "app" / "data" / "ASCII"
FIXTURES = REPO / "java" / "interest-service" / "src" / "test" / "resources" / "fixtures"

# file -> (record length, key slice)
LAYOUT = {
    "tcatbal.txt": (50, slice(0, 17)),   # CVTRA01Y  ACCT-ID(11) TYPE-CD(2) CAT-CD(4)
    "discgrp.txt": (50, slice(0, 16)),   # CVTRA02Y  GROUP-ID(10) TYPE-CD(2) CAT-CD(4)
    "acctdata.txt": (300, slice(0, 11)),  # CVACT01Y  ACCT-ID(11)
    "cardxref.txt": (50, slice(0, 16)),  # CVACT03Y  CARD-NUM(16)
}

POS = "{ABCDEFGHI"
NEG = "}JKLMNOPQR"


def zoned(value: str | Decimal, digits: int) -> str:
    """Format a decimal (scale 2) as PIC S9(digits-2)V99 with overpunch sign."""
    d = Decimal(value).quantize(Decimal("0.01"))
    s = f"{abs(d):.2f}".replace(".", "").rjust(digits, "0")
    assert len(s) == digits, (value, digits)
    table = NEG if d < 0 else POS
    return s[:-1] + table[int(s[-1])]


def normalise(name: str, lines: list[str]) -> list[str]:
    reclen, key = LAYOUT[name]
    out = []
    for line in lines:
        line = line.rstrip("\r\n")
        if not line.strip():
            continue
        assert len(line) <= reclen, (name, len(line), line)
        out.append(line.ljust(reclen))
    out.sort(key=lambda r: r[key])
    return out


def read_src(name: str) -> list[str]:
    return normalise(name, (SRC / name).read_text(encoding="ascii").splitlines())


def write(scenario: str, files: dict[str, list[str]]) -> None:
    d = FIXTURES / scenario / "input"
    d.mkdir(parents=True, exist_ok=True)
    for name, recs in files.items():
        recs = normalise(name, recs)
        (d / name).write_text("".join(r + "\n" for r in recs), encoding="ascii")
        print(f"  {scenario}/input/{name}: {len(recs)} records")


# ---------------------------------------------------------------- record builders
def tcatbal(acct: int, type_cd: str, cat_cd: int, bal: str) -> str:
    rec = f"{acct:011d}{type_cd}{cat_cd:04d}{zoned(bal, 11)}"
    return rec.ljust(50)


def set_acct_group(rec: str, group: str) -> str:
    # ACCT-GROUP-ID is bytes 113-122 (1-based), i.e. rec[112:122]
    return rec[:112] + group.ljust(10)[:10] + rec[122:]


def set_acct_curr_bal(rec: str, bal: str) -> str:
    # ACCT-CURR-BAL PIC S9(10)V99 is bytes 13-24 (1-based), i.e. rec[12:24]
    return rec[:12] + zoned(bal, 12) + rec[24:]


def acct_id(rec: str) -> int:
    return int(rec[0:11])


# ---------------------------------------------------------------- scenarios
def sample_data() -> dict[str, list[str]]:
    return {n: read_src(n) for n in LAYOUT}


def nonzero_balances() -> dict[str, list[str]]:
    accts = read_src("acctdata.txt")
    edits = {
        1: ("A000000000", None),        # explicit group with real rates
        2: ("ZEROAPR", None),           # every rate 0 -> no transactions
        3: ("NOSUCHGRP", "-500.00"),    # group missing -> DEFAULT rates; negative bal
        4: (None, None),                # blank group (as shipped) -> DEFAULT
        5: ("A000000000", None),
        6: ("A000000000", None),
        7: ("A000000000", None),        # LAST account: interest tx written but never posted
    }
    out = []
    for rec in accts:
        a = acct_id(rec)
        if a in edits:
            group, bal = edits[a]
            if group is not None:
                rec = set_acct_group(rec, group)
            if bal is not None:
                rec = set_acct_curr_bal(rec, bal)
        out.append(rec)

    tcat = [
        # acct 1, group A000000000: 15% / 25% / 0% (02-0001 has rate 0 -> no tx)
        tcatbal(1, "01", 1, "100.53"),      # 100.53*15/1200 = 1.256625 -> 1.25
        tcatbal(1, "01", 2, "-2500.00"),    # -2500*25/1200 = -52.0833  -> -52.08
        tcatbal(1, "02", 1, "999.99"),      # rate 0 -> skipped
        # acct 2, group ZEROAPR: all rates 0 -> account rewritten, no tx
        tcatbal(2, "01", 1, "5000.00"),
        tcatbal(2, "03", 1, "-10.00"),
        # acct 3, group NOSUCHGRP -> DEFAULT group used (15%), curr bal -500.00
        tcatbal(3, "01", 1, "1234.56"),     # 1234.56*15/1200 = 15.432 -> 15.43
        tcatbal(3, "04", 2, "-1.00"),       # -1*15/1200 = -0.0125    -> -0.01
        # acct 4, blank group -> DEFAULT
        tcatbal(4, "01", 3, "100000000.00"),  # 1e8*25/1200 = 2083333.333 -> 2083333.33
        tcatbal(4, "06", 1, "47.99"),         # 47.99*15/1200 = 0.599875 -> 0.59
        # acct 5, group A000000000: two tx that cancel out (total 0.00)
        tcatbal(5, "05", 1, "-800.00"),     # -10.00
        tcatbal(5, "07", 1, "800.00"),      # +10.00 (A000000000 07-0001 = 15%, DEFAULT would be 0)
        # acct 6, group A000000000: tiny balance -> 0.00 tx still written
        tcatbal(6, "01", 4, "0.01"),        # 0.01*25/1200 = 0.000208 -> 0.00
        # acct 7, group A000000000, last account in the file: CBACT04C's main loop
        # never reaches 1050-UPDATE-ACCOUNT for the final control group, so the
        # 15.00 transaction is written but ACCT-CURR-BAL stays unchanged.
        tcatbal(7, "01", 1, "1200.00"),     # 1200*15/1200 = 15.00
    ]
    return {
        "tcatbal.txt": tcat,
        "discgrp.txt": read_src("discgrp.txt"),
        "acctdata.txt": out,
        "cardxref.txt": read_src("cardxref.txt"),
    }


def single_account() -> dict[str, list[str]]:
    accts = [set_acct_group(r, "A000000000") for r in read_src("acctdata.txt") if acct_id(r) == 1]
    xref = [r for r in read_src("cardxref.txt") if int(r[25:36]) == 1]
    tcat = [
        tcatbal(1, "01", 1, "1000.00"),   # 1000*15/1200 = 12.50
        tcatbal(1, "01", 2, "-333.33"),   # -333.33*25/1200 = -6.944375 -> -6.94
        tcatbal(1, "01", 3, "0.50"),      # 0.5*25/1200 = 0.0104 -> 0.01
    ]
    return {
        "tcatbal.txt": tcat,
        "discgrp.txt": read_src("discgrp.txt"),
        "acctdata.txt": accts,
        "cardxref.txt": xref,
    }


SCENARIOS = {
    "sample-data": sample_data,
    "nonzero-balances": nonzero_balances,
    "single-account": single_account,
}


def main(argv: list[str]) -> int:
    names = argv[1:] or list(SCENARIOS)
    for name in names:
        print(f"building {name}")
        write(name, SCENARIOS[name]())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
