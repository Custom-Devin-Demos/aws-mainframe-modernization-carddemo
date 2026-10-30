      *> Test stub for the LE abend service CEE3ABD (parity harness only).
      *> Prints the abend code and ends the run unit with exit status 99.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  LK-ABCODE               PIC S9(9) BINARY.
       01  LK-TIMING               PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LK-ABCODE LK-TIMING.
           DISPLAY 'CEE3ABD ABCODE=' LK-ABCODE ' TIMING=' LK-TIMING
           STOP RUN RETURNING 99.
