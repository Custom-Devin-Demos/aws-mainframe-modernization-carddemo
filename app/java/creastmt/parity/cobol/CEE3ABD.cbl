       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CEE3ABD.
      ******************************************************************
      * Parity-harness stub for the LE abend service (z/OS only).
      ******************************************************************
       PROCEDURE DIVISION.
           DISPLAY 'CEE3ABD: ABEND REQUESTED'
           STOP RUN RETURNING 999.
