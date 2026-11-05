package com.carddemo.online.loader;

/** Sample data sets under app/data and their copybook record lengths. */
public enum Dataset {
    USRSEC(null, "AWS.M2.CARDDEMO.USRSEC.PS", 80),
    CUSTDATA("custdata.txt", "AWS.M2.CARDDEMO.CUSTDATA.PS", 500),
    ACCTDATA("acctdata.txt", "AWS.M2.CARDDEMO.ACCTDATA.PS", 300),
    CARDDATA("carddata.txt", "AWS.M2.CARDDEMO.CARDDATA.PS", 150),
    CARDXREF("cardxref.txt", "AWS.M2.CARDDEMO.CARDXREF.PS", 50),
    TRANTYPE("trantype.txt", "AWS.M2.CARDDEMO.TRANTYPE.PS", 60),
    TRANCATG("trancatg.txt", "AWS.M2.CARDDEMO.TRANCATG.PS", 60),
    TCATBALF("tcatbal.txt", "AWS.M2.CARDDEMO.TCATBALF.PS", 50),
    DISCGRP("discgrp.txt", "AWS.M2.CARDDEMO.DISCGRP.PS", 50),
    /** The TRANSACT KSDS is seeded from the daily transaction file (DALYTRAN.PS.INIT is a single empty record). */
    DALYTRAN("dailytran.txt", "AWS.M2.CARDDEMO.DALYTRAN.PS", 350);

    private final String asciiFile;
    private final String ebcdicFile;
    private final int recordLength;

    Dataset(String asciiFile, String ebcdicFile, int recordLength) {
        this.asciiFile = asciiFile;
        this.ebcdicFile = ebcdicFile;
        this.recordLength = recordLength;
    }

    public String asciiFile() {
        return asciiFile;
    }

    public String ebcdicFile() {
        return ebcdicFile;
    }

    public int recordLength() {
        return recordLength;
    }
}
