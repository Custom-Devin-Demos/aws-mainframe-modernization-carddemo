#!/usr/bin/env python3
"""Writes the hand-designed edge-case ACCTFILE unloads (CVACT01Y, 300-byte ASCII lines).

Signed fields use the EBCDIC-derived overpunch of the sample data ('{', 'A'-'I' positive,
'}', 'J'-'R' negative). Run from anywhere: python3 make_edge_inputs.py <fixtures-dir>.
"""
import sys
from decimal import Decimal
from pathlib import Path

POS, NEG = "{ABCDEFGHI", "}JKLMNOPQR"


def zoned(value, digits=12, scale=2):
    unscaled = int((Decimal(value) * (10 ** scale)).to_integral_value())
    body = str(abs(unscaled)).rjust(digits, "0")
    if len(body) > digits:
        raise ValueError(value)
    last = int(body[-1])
    return body[:-1] + (NEG[last] if unscaled < 0 else POS[last])


def account(acct_id, status="Y", bal="0", credit="1000.00", cash="500.00", opened="2020-01-01",
            expires="2026-01-01", reissue="2026-01-01", cyc_credit="0", cyc_debit="0",
            zip_code="", group=""):
    rec = (str(acct_id).rjust(11, "0") + status.ljust(1)[:1] + zoned(bal) + zoned(credit) + zoned(cash)
           + opened.ljust(10)[:10] + expires.ljust(10)[:10] + reissue.ljust(10)[:10]
           + zoned(cyc_credit) + zoned(cyc_debit) + zip_code.ljust(10)[:10] + group.ljust(10)[:10])
    rec = rec.ljust(300)
    assert len(rec) == 300
    return rec


SCENARIOS = {
    # BR-CBACT01C-07: OUT-ACCT-CURR-CYC-DEBIT only MOVEd when the input debit is zero.
    "cyc-debit-carryover": [
        account(101, bal="100.00", cyc_debit="125.50"),
        account(102, bal="-20.00", cyc_debit="-40.00"),
        account(103, status="N", cyc_debit="0"),
        account(104, cyc_debit="9.99"),
        account(105, cyc_debit="0", cyc_credit="12.34"),
        account(106, cyc_debit="0.01"),
    ],
    # BR-CBACT01C-01/-06/-08..-10: key order, sign handling, extreme magnitudes, blank/odd codes.
    "field-values-and-key-order": [
        account(300, status="N", bal="-1234.56", credit="0", cash="-0.01", cyc_credit="777.77",
                cyc_debit="-5.00", zip_code="12345", group="GOLDGROUP1"),
        account(7, status=" ", bal="9999999999.99", credit="9999999999.99", cash="0",
                group="A000000001"),
        account(99999999999, bal="-9999999999.99", credit="1.00", cash="0.10", cyc_credit="-0.01",
                opened="2019-02-30", expires="9999-12-31", reissue="1999-12-31"),
        account(42, status="x", bal="0.09", cyc_debit="0", zip_code="ZIP,WITH\"Q", group=" LEADING"),
        account(1000, bal="0", credit="0", cash="0", opened="", expires="", reissue="2000-01-01"),
    ],
    # BR-CBACT01C-05: COBDATFT type 2 -> 2 moves bytes 1-4, 6-7, 9-10 whatever they contain.
    "reissue-date-formats": [
        account(1, reissue="2025-05-20"),
        account(2, reissue="20250520"),
        account(3, reissue=""),
        account(4, reissue="2025/05/20"),
        account(5, reissue=" 025-05-20"),
        account(6, reissue="25-05-2020"),
        account(7, reissue="ABCD-EF-GH"),
    ],
    "empty-acctfile": [],
    # Single good record used by the OPEN-failure scenarios.
    "outfile-open-error": [account(1)],
    "arryfile-open-error": [account(1)],
    "vbrcfile-open-error": [account(1)],
    # Record 2 has a non-numeric ACCT-CURR-BAL; see fixtures/README.md (hand-derived tail).
    "acctfile-read-error": [
        account(1, bal="10.00"),
        account(2).replace(zoned("0"), "00000ABC940{", 1),
        account(3),
    ],
}


def main():
    root = Path(sys.argv[1])
    for name, records in SCENARIOS.items():
        d = root / name / "input"
        d.mkdir(parents=True, exist_ok=True)
        (d / "acctdata.txt").write_text("".join(r + "\n" for r in records), encoding="ascii")
        print(f"{name}: {len(records)} records")


if __name__ == "__main__":
    main()
