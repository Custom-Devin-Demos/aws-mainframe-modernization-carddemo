      ******************************************************************
      * Parity harness only: stand-in for the z/OS LE abend service so
      * the 9999-ABEND-PROGRAM path terminates with a non-zero RC.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  LK-ABCODE              PIC S9(9) BINARY.
       01  LK-TIMING              PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LK-ABCODE LK-TIMING.
           DISPLAY 'CEE3ABD ABEND CODE ' LK-ABCODE
           MOVE 16 TO RETURN-CODE
           STOP RUN.
