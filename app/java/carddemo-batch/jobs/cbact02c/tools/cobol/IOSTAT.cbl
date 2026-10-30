      *****************************************************************
      * Test driver for 9910-DISPLAY-IO-STATUS. run.sh extracts the
      * IO-STATUS / TWO-BYTES / IO-STATUS-04 items (WS9910.cpy) and the
      * paragraph itself (P9910.cpy) verbatim from app/cbl/CBACT02C.cbl
      * and this driver PERFORMs it for a list of file statuses.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. IOSTAT.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY 'WS9910.cpy'.
       01  STATUS-LIST             PIC X(42) VALUE
           '000410222330353739414246479091929396970A9A'.
       01  IX                      PIC 99.
       PROCEDURE DIVISION.
           PERFORM VARYING IX FROM 1 BY 2 UNTIL IX > 42
               MOVE STATUS-LIST(IX:2) TO IO-STATUS
               DISPLAY IO-STATUS ',' WITH NO ADVANCING
               PERFORM 9910-DISPLAY-IO-STATUS
           END-PERFORM
           STOP RUN.
       COPY 'P9910.cpy'.
