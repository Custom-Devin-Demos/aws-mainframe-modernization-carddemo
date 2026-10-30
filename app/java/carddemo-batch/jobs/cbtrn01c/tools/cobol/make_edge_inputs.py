#!/usr/bin/env python3
"""Builds the edge-case fixture inputs deterministically from app/data/ASCII.

Every scenario uses records copied byte-for-byte from the sample files; the only edits are the
ones listed per scenario (a replaced DALYTRAN-CARD-NUM, or an ACCTFILE record left out).
CUSTFILE, CARDFILE and TRANFILE are opened but never read by CBTRN01C, so edge scenarios
supply them as empty files.

usage: make_edge_inputs.py SAMPLE_DIR FIXTURES_DIR
"""
import os
import sys

CARD_NUM = slice(262, 278)          # DALYTRAN-CARD-NUM, CVTRA06Y offset 263 length 16
UNKNOWN_CARD = "9999999999999999"   # not in cardxref.txt
UNKNOWN_CARD_2 = "0000000000000000"  # not in cardxref.txt


def lines(path):
    with open(path, encoding="ascii") as f:
        return [l.rstrip("\r\n") for l in f if l.strip()]


def with_card(rec, card):
    return rec[:CARD_NUM.start] + card + rec[CARD_NUM.stop:]


def write(path, recs):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="ascii", newline="\n") as f:
        f.writelines(r + "\n" for r in recs)


def main(sample, fixtures):
    daily = lines(os.path.join(sample, "dailytran.txt"))
    xref = {l[:16]: l for l in lines(os.path.join(sample, "cardxref.txt"))}
    acct = {l[:11]: l for l in lines(os.path.join(sample, "acctdata.txt"))}
    for card in (UNKNOWN_CARD, UNKNOWN_CARD_2):
        assert card not in xref

    # First five daily records whose cards resolve to five different accounts.
    picked, seen = [], set()
    for rec in daily:
        acct_id = xref[rec[CARD_NUM]][25:36]
        if acct_id not in seen:
            seen.add(acct_id)
            picked.append(rec)
        if len(picked) == 5:
            break
    d1, d2, d3, d4, d5 = picked

    def xref_of(*recs):
        return [xref[r[CARD_NUM]] for r in recs]

    def acct_of(xrefs):
        return [acct[x[25:36]] for x in xrefs]

    def scenario(name, dalytran, xrefs, accts):
        base = os.path.join(fixtures, name, "input")
        write(os.path.join(base, "dailytran.txt"), dalytran)
        write(os.path.join(base, "cardxref.txt"), xrefs)
        write(os.path.join(base, "acctdata.txt"), accts)
        for empty in ("custdata.txt", "carddata.txt", "transact.txt"):
            write(os.path.join(base, empty), [])

    # Card not in XREF, in the middle and as the last record (so the EOF re-lookup also fails).
    x = xref_of(d1, d2, d3)
    scenario("xref-not-found", [d1, with_card(d2, UNKNOWN_CARD), with_card(d3, UNKNOWN_CARD_2)], x, acct_of(x))

    # XREF found but its account is missing from ACCTFILE; last record is the missing one.
    x = xref_of(d1, d2)
    scenario("account-not-found", [d1, d2], x, acct_of(x[:1]))

    # Empty DALYTRAN: first READ hits EOF, the lookup still runs on the initial DALYTRAN-RECORD.
    x = xref_of(d1, d2, d3, d4, d5)
    scenario("empty-dalytran", [], x, acct_of(x))

    # All three outcomes interleaved; the last record verifies.
    x = xref_of(d1, d3, d4, d5)
    accts = [a for a in acct_of(x) if a[:11] != xref[d3[CARD_NUM]][25:36]]
    scenario("mixed-outcomes", [d1, with_card(d2, UNKNOWN_CARD), d3, d4, d5], x, accts)

    # sample-data has no TRANSACT unload (CBTRN02C creates it): TRANFILE is an empty KSDS.
    write(os.path.join(fixtures, "sample-data", "input", "transact.txt"), [])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
