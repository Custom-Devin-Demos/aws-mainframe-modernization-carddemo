package com.carddemo.online.loader;

/** Which copy of the sample data under app/data to read. */
public enum DataEncoding {
    /** app/data/ASCII/*.txt: newline-delimited, trailing FILLER sometimes truncated. */
    ASCII,
    /** app/data/EBCDIC/AWS.M2.CARDDEMO.*.PS: fixed-length records in code page 037, no delimiters. */
    EBCDIC
}
