#!/usr/bin/env python3
"""Generate the INTCALC parity sample data set.

Writes line-sequential ASCII files laid out exactly like the CardDemo copybooks
(CVTRA01Y, CVACT03Y, CVTRA02Y, CVACT01Y). Signed DISPLAY numerics use the
EBCDIC-style trailing overpunch already used by app/data/ASCII ('{' = +0, '}' = -0).

Each account is designed to exercise one or more CBACT04C business rules; see
modernization/intcalc/BUSINESS_RULES.md.
"""
from decimal import Decimal
from pathlib import Path

POS = "{ABCDEFGHI"
NEG = "}JKLMNOPQR"


def zoned(value, int_digits, scale):
    d = Decimal(str(value))
    digits = f"{int(abs(d).scaleb(scale).to_integral_exact()):0{int_digits + scale}d}"
    if len(digits) != int_digits + scale:
        raise ValueError(f"{value} does not fit S9({int_digits})V9({scale})")
    last = int(digits[-1])
    return digits[:-1] + (NEG[last] if d < 0 else POS[last])


def x(value, length):
    if len(value) > length:
        raise ValueError(value)
    return value.ljust(length)


def n(value, length):
    return f"{int(value):0{length}d}"


# (group, type, cat, rate)
DISCGRP = [
    ("GOLD", "01", 1, "19.99"),
    ("GOLD", "01", 2, "24.99"),
    ("GOLD", "02", 1, "0.00"),
    ("DEFAULT", "01", 1, "15.00"),
    ("DEFAULT", "01", 2, "25.00"),
    ("DEFAULT", "01", 3, "21.37"),
    ("DEFAULT", "02", 1, "0.00"),
    ("DEFAULT", "02", 2, "7.77"),
    ("DEFAULT", "03", 1, "12.34"),
    ("ZEROAPR", "01", 1, "0.00"),
]

# acct_id: (curr_bal, credit_limit, cash_limit, cyc_credit, cyc_debit, group)
ACCOUNTS = {
    101: ("1500.00", "5000.00", "1000.00", "120.50", "75.25", "GOLD"),
    102: ("-250.75", "3000.00", "500.00", "10.00", "0.00", "NOGROUP"),
    103: ("5000.00", "10000.00", "2000.00", "300.00", "450.00", "ZEROAPR"),
    104: ("800.00", "2500.00", "250.00", "0.00", "99.99", "GOLD"),
    105: ("999999999.99", "999999999.99", "100000.00", "0.00", "0.00", "GOLD"),
    106: ("0.00", "1000.00", "100.00", "5.00", "5.00", "DEFAULT"),
    107: ("42.00", "1000.00", "100.00", "1.00", "2.00", "DEFAULT"),
    108: ("1000.00", "2000.00", "200.00", "50.00", "60.00", "NOGROUP"),
}

# (acct_id, card_num, cust_id); account 104 has two cards
XREF = [
    (101, "4000000000000101", 900000101),
    (102, "4000000000000102", 900000102),
    (103, "4000000000000103", 900000103),
    (104, "4000000000001041", 900000104),
    (104, "4000000000001042", 900000104),
    (105, "4000000000000105", 900000105),
    (106, "4000000000000106", 900000106),
    (107, "4000000000000107", 900000107),
    (108, "4000000000000108", 900000108),
]

# (acct_id, type, cat, balance). Written deliberately out of key order: the
# KSDS (and the Java job) must process them in key order.
TCATBAL = [
    (108, "03", 1, "1000.00"),
    (101, "01", 2, "999.99"),
    (101, "01", 1, "1234.56"),
    (101, "01", 3, "500.00"),
    (101, "02", 1, "300.00"),
    (102, "01", 1, "-250.75"),
    (102, "02", 2, "0.01"),
    (102, "01", 3, "-0.06"),
    (103, "01", 1, "5000.00"),
    (104, "01", 1, "100.00"),
    (104, "01", 2, "0.48"),
    (105, "01", 2, "999999999.99"),
    (105, "01", 1, "123456789.01"),
    (106, "02", 1, "777.77"),
    (106, "02", 2, "1200.00"),
]


def main():
    out = Path(__file__).resolve().parent / "sample"
    out.mkdir(parents=True, exist_ok=True)

    with open(out / "discgrp.txt", "w") as f:
        for g, t, c, r in DISCGRP:
            f.write(x(g, 10) + x(t, 2) + n(c, 4) + zoned(r, 4, 2) + " " * 28 + "\n")

    with open(out / "acctdata.txt", "w") as f:
        for acct, (bal, cl, ccl, cc, cd, grp) in sorted(ACCOUNTS.items()):
            f.write(
                n(acct, 11) + "Y" + zoned(bal, 10, 2) + zoned(cl, 10, 2)
                + zoned(ccl, 10, 2) + "2020-01-01" + "2030-12-31" + "2025-01-01"
                + zoned(cc, 10, 2) + zoned(cd, 10, 2) + x("94105", 10)
                + x(grp, 10) + " " * 178 + "\n"
            )

    with open(out / "cardxref.txt", "w") as f:
        for acct, card, cust in sorted(XREF, key=lambda r: r[1]):
            f.write(x(card, 16) + n(cust, 9) + n(acct, 11) + " " * 14 + "\n")

    with open(out / "tcatbal.txt", "w") as f:
        for acct, t, c, bal in TCATBAL:
            f.write(n(acct, 11) + x(t, 2) + n(c, 4) + zoned(bal, 9, 2) + " " * 22 + "\n")


if __name__ == "__main__":
    main()
