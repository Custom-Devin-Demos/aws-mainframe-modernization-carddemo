#!/usr/bin/env python3
"""Writes the hand-built edge-case inputs (legacy ASCII unload format) under
src/test/resources/fixtures/<scenario>/input/. Deterministic; rerun after editing a scenario, then run
run_cobol.sh on each scenario to regenerate its expected/ files.

Non-key fields that the program does not use are copied from the first sample records so the inputs
look like real unloads.
"""
import os
import sys
from decimal import Decimal

POS, NEG = "{ABCDEFGHI", "}JKLMNOPQR"


def zoned(value, int_digits, scale=2):
    unscaled = int((Decimal(value) * (10 ** scale)).to_integral_value())
    digits = str(abs(unscaled)).rjust(int_digits + scale, "0")
    sign = NEG if unscaled < 0 else POS
    return digits[:-1] + sign[int(digits[-1])]


def xref(card, cust, acct):
    return card.ljust(16) + cust.rjust(9, "0") + acct.rjust(11, "0") + " " * 14


def acct(acct_id, bal, limit, exp, cyc_credit="0", cyc_debit="0", cash_limit="500"):
    return (acct_id.rjust(11, "0") + "Y" + zoned(bal, 10) + zoned(limit, 10) + zoned(cash_limit, 10)
            + "2014-11-20" + exp + "2025-05-20" + zoned(cyc_credit, 10) + zoned(cyc_debit, 10)
            + "A000000000").ljust(300)


def tcat(acct_id, type_cd, cat_cd, bal):
    return acct_id.rjust(11, "0") + type_cd + cat_cd + zoned(bal, 9) + "0" * 22


def tran(tran_id, card, amt, type_cd="01", cat_cd="0001", orig_ts="2022-06-10 19:27:53.000000",
         desc="Purchase at Abshire-Lowe"):
    rec = (tran_id.rjust(16, "0") + type_cd + cat_cd + "POS TERM".ljust(10) + desc.ljust(100) + zoned(amt, 9)
           + "800000000" + "Abshire-Lowe".ljust(50) + "North Enoshaven".ljust(50) + "72112".ljust(10)
           + card.ljust(16) + orig_ts.ljust(26) + " " * 26)
    return rec.ljust(350)


C = ["4000000000000001", "4000000000000002", "4000000000000003", "4000000000000004",
     "4000000000000005", "4000000000000006", "4000000000000007"]

SCENARIOS = {
    # Every reject reason, 102+103 together (103 wins), and the >= boundaries of both checks.
    "reject-reasons": {
        "cardxref.txt": [xref(C[0], "1", "1"), xref(C[1], "2", "99"), xref(C[2], "3", "3"),
                         xref(C[3], "4", "4"), xref(C[4], "5", "5"), xref(C[5], "6", "6")],
        "acctdata.txt": [acct("1", "100", "1000", "2025-05-20"),
                         acct("3", "0", "100", "2025-05-20", cyc_credit="60", cyc_debit="-20"),
                         acct("4", "0", "1000", "2022-06-09"),
                         acct("5", "0", "100", "2022-06-01", cyc_credit="90"),
                         acct("6", "0", "200", "2022-06-10", cyc_credit="150")],
        "tcatbal.txt": [tcat("1", "01", "0001", "10")],
        "dailytran.txt": [tran("101", "4999999999999999", "10.00"),          # 100 card not in xref
                          tran("102", C[1], "10.00"),                        # 101 account not found
                          tran("103", C[2], "20.01"),                        # 102: 60 - (-20) + 20.01 > 100
                          tran("104", C[3], "1.00"),                         # 103: expired 2022-06-09
                          tran("105", C[4], "50.00"),                        # 102 and 103 -> 103
                          tran("106", C[5], "50.00"),                        # limit == temp bal, exp == date: posts
                          tran("107", C[0], "25.00"),                        # valid, updates tcatbal
                          tran("108", C[2], "20.00")],                       # 60 + 20 + 20 = 100 == limit: posts
    },
    # TCATBAL update of an existing record, create of a missing one, then update of the created one.
    "tcatbal-create-update": {
        "cardxref.txt": [xref(C[0], "1", "1"), xref(C[1], "2", "2")],
        "acctdata.txt": [acct("1", "100", "5000", "2025-05-20"), acct("2", "0", "5000", "2025-05-20")],
        "tcatbal.txt": [tcat("1", "01", "0001", "10"), tcat("2", "03", "0002", "-5")],
        "dailytran.txt": [tran("201", C[0], "15.25"),                        # update 1/01/0001
                          tran("202", C[0], "7.00", type_cd="02", cat_cd="0001"),   # create 1/02/0001
                          tran("203", C[0], "3.00", type_cd="02", cat_cd="0001"),   # update created record
                          tran("204", C[1], "1.00", type_cd="01", cat_cd="0003"),   # create 2/01/0003
                          tran("205", C[1], "2.50", type_cd="03", cat_cd="0002")],  # update 2/03/0002
    },
    # Credit vs debit routing: amt >= 0 (including zero) adds to cycle credit, negative adds to cycle debit.
    # Because CYC-DEBIT holds negative amounts, CREDIT - DEBIT grows with every debit (see quirks).
    "negative-amounts": {
        "cardxref.txt": [xref(C[0], "1", "1"), xref(C[1], "2", "2")],
        "acctdata.txt": [acct("1", "-10", "100", "2025-05-20", cyc_credit="30", cyc_debit="-40"),
                         acct("2", "0", "50", "2025-05-20", cyc_credit="0", cyc_debit="-60")],
        "tcatbal.txt": [tcat("1", "03", "0001", "-1")],
        "dailytran.txt": [tran("301", C[0], "-25.50", type_cd="03"),         # debit; 30-(-40)-25.50 <= 100
                          tran("302", C[0], "0.00"),                         # zero counts as credit
                          tran("303", C[0], "-0.01", type_cd="03"),          # debit
                          tran("304", C[1], "-10.00", type_cd="03"),         # 0+60-10 = 50 == limit: posts
                          tran("305", C[1], "1.00"),                         # 0-(-70)+1 = 71 > 50: 102
                          tran("306", C[0], "5.00")],                        # 30-(-65.51)+5 > 100: 102
    },
    # Picture truncation: WS-TEMP-BAL S9(09)V99, ACCT-* S9(10)V99, TRAN-CAT-BAL S9(09)V99.
    "picture-truncation": {
        "cardxref.txt": [xref(C[0], "1", "1"), xref(C[1], "2", "2"), xref(C[2], "3", "3")],
        "acctdata.txt": [acct("1", "9999999999.50", "100", "2025-05-20", cyc_credit="9999999999.00"),
                         acct("2", "0", "100", "2025-05-20", cyc_credit="1000000050.00"),
                         acct("3", "-9999999999.00", "100", "2025-05-20", cyc_debit="-9999999999.99")],
        "tcatbal.txt": [tcat("1", "01", "0001", "999999999.99"), tcat("3", "03", "0001", "-999999999.99")],
        "dailytran.txt": [tran("401", C[0], "1.00"),     # temp 10000000000.00 -> 000000000.00: posts; balances wrap
                          tran("402", C[1], "1.00"),     # temp 1000000051.00 -> 000000051.00: posts (not 102)
                          tran("403", C[2], "-1.00", type_cd="03"),   # temp 9999999998.99 -> 999999998.99: 102
                          tran("404", C[0], "999999999.99")],         # wrapped credit 0 + 999999999.99 > 100: 102
    },
    # Duplicate TRAN-ID among posted records: WRITE TRANFILE status 22 -> 9999-ABEND-PROGRAM.
    "duplicate-tran-id-abend": {
        "cardxref.txt": [xref(C[0], "1", "1")],
        "acctdata.txt": [acct("1", "0", "1000", "2025-05-20")],
        "tcatbal.txt": [],
        "dailytran.txt": [tran("501", C[0], "1.00"), tran("502", C[0], "2.00"), tran("501", C[0], "3.00"),
                          tran("503", C[0], "4.00")],
    },
}

if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/fixtures"
    for name, files in SCENARIOS.items():
        d = os.path.join(root, name, "input")
        os.makedirs(d, exist_ok=True)
        for fname, lines in files.items():
            with open(os.path.join(d, fname), "w", newline="\n") as f:
                f.write("".join(l + "\n" for l in lines))
        print(name)
