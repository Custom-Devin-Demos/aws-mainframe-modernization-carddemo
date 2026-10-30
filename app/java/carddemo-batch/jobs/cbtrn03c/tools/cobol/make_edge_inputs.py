#!/usr/bin/env python3
"""Writes the hand-designed edge-case fixture inputs (src/test/resources/fixtures/<scenario>/input).

Each scenario targets one rule or branch the sample data does not exercise; see fixtures/README.md.
Card numbers come from app/data/ASCII/cardxref.txt so the sample KSDS files can be reused.
"""
import os
import shutil
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.abspath(os.path.join(HERE, "../../src/test/resources/fixtures"))

C1, C2, C3, C4, C5 = ("0500024453765740", "0683586198171516", "0923877193247330",
                      "0927987108636232", "0982496213629795")
UNKNOWN_CARD = "9999999999999999"
POSITIVE, NEGATIVE = "{ABCDEFGHI", "}JKLMNOPQR"
SAMPLE_KSDS = {"cardxref": "app/data/ASCII/cardxref.txt", "trantype": "app/data/ASCII/trantype.txt",
               "trancatg": "app/data/ASCII/trancatg.txt"}


def zoned(amount):
    cents = int((Decimal(amount) * 100).to_integral_value())
    digits = "%011d" % abs(cents)
    table = NEGATIVE if cents < 0 else POSITIVE
    return digits[:-1] + table[int(digits[-1])]


def tran(tid, card, proc_ts, amount, type_cd="01", cat_cd="0001", source="POS TERM", desc=None):
    orig_ts = proc_ts if proc_ts.strip() else "2022-06-10 10:00:00.000000"
    rec = ("%016d" % tid + type_cd + cat_cd + source.ljust(10) + (desc or "Edge case %d" % tid).ljust(100)
           + zoned(amount) + "000000001" + "Edge Merchant".ljust(50) + "Edge City".ljust(50) + "12345".ljust(10)
           + card + orig_ts.ljust(26) + proc_ts.ljust(26) + " " * 20)
    assert len(rec) == 350, len(rec)
    return rec


def ts(day, time="12:00:00"):
    return "%s %s.000000" % (day, time)


def write(scenario, files, ksds=("cardxref", "trantype", "trancatg"), params=None):
    d = os.path.join(FIXTURES, scenario, "input")
    if os.path.isdir(d):
        shutil.rmtree(d)
    os.makedirs(d)
    for name, lines in files.items():
        with open(os.path.join(d, name + ".txt"), "w", encoding="ascii", newline="\n") as f:
            f.writelines(l + "\n" for l in lines)
    for name in ksds:
        if name not in files:
            with open(os.path.join(d, name + ".from"), "w") as f:
                f.write(SAMPLE_KSDS[name] + "\n")
    if params:
        with open(os.path.join(d, "params.properties"), "w") as f:
            f.writelines("%s=%s\n" % kv for kv in params.items())


FULL_RANGE = ["2022-01-01 2022-07-06"]


def main():
    # REPRO key order, SORT by card (EQUALS), INCLUDE boundaries; input deliberately not in key order.
    write("presort-filter-order", {"dateparm": FULL_RANGE, "tranfile": [
        tran(50, C3, ts("2022-03-01"), "10.00"),
        tran(10, C3, ts("2022-03-02"), "11.00"),
        tran(30, C1, ts("2021-12-31", "23:59:59"), "12.00"),      # day before start: excluded
        tran(20, C1, ts("2022-01-01", "00:00:00"), "13.00"),      # start date: included
        tran(40, C1, ts("2022-07-06", "23:59:59"), "14.00"),      # end date (time ignored): included
        tran(60, C1, ts("2022-07-07", "00:00:00"), "15.00"),      # day after end: excluded
        tran(70, C2, " " * 26, "16.00"),                          # blank TRAN-PROC-TS: excluded
        tran(15, C2, ts("2022-05-05"), "17.00"),
        tran(5, C3, ts("2022-04-04"), "18.00"),
    ]})

    # Page break at MOD(line counter, 20) = 0 inside an account, and an account total stepping over
    # a multiple of 20 so that no break happens there.
    write("pagination", {"dateparm": FULL_RANGE, "tranfile":
        [tran(100 + i, C1, ts("2022-06-01"), "%d.%02d" % (i + 1, i)) for i in range(29)]
        + [tran(200 + i, C2, ts("2022-06-02"), "-%d.50" % (i + 1), type_cd="03") for i in range(20)]})

    # EOF: last TRAN-AMT added again to page/account totals; no account total for the last card.
    write("eof-last-account", {"dateparm": FULL_RANGE, "tranfile": [
        tran(1, C4, ts("2022-02-01"), "100.00"), tran(2, C4, ts("2022-02-02"), "200.00"),
        tran(3, C5, ts("2022-02-03"), "30.00"), tran(4, C5, ts("2022-02-04"), "40.00"),
        tran(5, C5, ts("2022-02-05"), "5.25")]})

    # Edited pictures (zero, sub-unit, negative, maximum) and S9(9)V99 high-order truncation of
    # the totals; long descriptions truncated to X(15) / X(29).
    write("edited-amounts-overflow", {"dateparm": FULL_RANGE,
        "trantype": ["01" + "Purchase with a description longer than 15".ljust(50) + " " * 8,
                     "03" + "Credit".ljust(50) + " " * 8],
        "trancatg": ["010001" + "Regular sales draft with a very long category text".ljust(50) + " " * 4,
                     "030001" + "Credit to Account".ljust(50) + " " * 4],
        "tranfile": [
            tran(1, C1, ts("2022-06-01"), "0.00"), tran(2, C1, ts("2022-06-01"), "0.05"),
            tran(3, C1, ts("2022-06-01"), "-0.05", type_cd="03"), tran(4, C1, ts("2022-06-01"), "1000.00"),
            tran(5, C1, ts("2022-06-01"), "-1234567.89", type_cd="03"),
            tran(6, C1, ts("2022-06-01"), "999999999.99"), tran(7, C1, ts("2022-06-01"), "999999999.99"),
            tran(8, C2, ts("2022-06-01"), "-999999999.99", type_cd="03"),
            tran(9, C2, ts("2022-06-01"), "-999999999.99", type_cd="03")]})

    # SORT INCLUDE range wider than DATEPARM: the in-program filter hits NEXT SENTENCE on C2 and the
    # loop ends there (C3 never reported, no totals).
    write("date-filter-next-sentence", {"dateparm": ["2022-03-01 2022-03-31"], "tranfile": [
        tran(1, C1, ts("2022-03-01", "00:00:00"), "10.00"), tran(2, C1, ts("2022-03-31", "23:59:59"), "20.00"),
        tran(3, C2, ts("2022-02-15"), "30.00"), tran(4, C2, ts("2022-03-15"), "40.00"),
        tran(5, C3, ts("2022-03-10"), "50.00")]},
        params={"SORT-START-DATE": "2022-01-01", "SORT-END-DATE": "2022-12-31"})

    # Nothing passes INCLUDE: the first READ hits EOF with TRAN-RECORD still initial (spaces), so the
    # date filter takes NEXT SENTENCE and not even the totals are written.
    write("no-selected-records", {"dateparm": ["2022-01-01 2022-01-31"], "tranfile": [
        tran(1, C1, ts("2022-06-01"), "10.00"), tran(2, C2, ts("2022-06-02"), "20.00")]})

    # DATEPARM present but empty: status 10 at 0550-DATEPARM-READ sets END-OF-FILE; no report lines.
    write("empty-dateparm", {"dateparm": [], "tranfile": [tran(1, C1, ts("2022-06-01"), "10.00")]})

    # No DATEPARM DD: dates from --param START-DATE / END-DATE.
    write("dateparm-from-params", {"tranfile": [
        tran(1, C1, ts("2022-06-10"), "10.00"), tran(2, C1, ts("2022-06-11"), "20.00"),
        tran(3, C2, ts("2022-06-10"), "30.00")]},
        params={"START-DATE": "2022-06-10", "END-DATE": "2022-06-10"})

    # No DATEPARM DD and no parameters: 0500-DATEPARM-OPEN fails (status 35) and abends.
    write("missing-dateparm-abend", {"tranfile": [tran(1, C1, ts("2022-06-01"), "10.00")]})

    write("invalid-xref", {"dateparm": FULL_RANGE, "tranfile": [
        tran(1, C1, ts("2022-06-01"), "10.00"), tran(2, UNKNOWN_CARD, ts("2022-06-01"), "20.00")]})
    write("invalid-trantype", {"dateparm": FULL_RANGE, "tranfile": [
        tran(1, C1, ts("2022-06-01"), "10.00"), tran(2, C1, ts("2022-06-01"), "20.00", type_cd="09")]})
    write("invalid-trancatg", {"dateparm": FULL_RANGE, "tranfile": [
        tran(1, C1, ts("2022-06-01"), "10.00"), tran(2, C1, ts("2022-06-01"), "20.00", cat_cd="0009")]})


if __name__ == "__main__":
    main()
