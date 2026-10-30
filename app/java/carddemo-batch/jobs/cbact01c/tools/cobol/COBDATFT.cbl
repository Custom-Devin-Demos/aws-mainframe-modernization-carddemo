      ******************************************************************
      * COBOL stand-in for the assembler routine app/asm/COBDATFT.asm
      * (DSECT app/maclib/COCDATFT.mac), used only by the GnuCOBOL
      * parity harness. Touches exactly the bytes the assembler touches;
      * every other byte of CODATECN-REC is left unchanged.
      * Assembler implicit lengths (CLC COINPDT+4 is 20 bytes, MVC COERMSG
      * is 38 bytes from a 13-byte literal) are ported as their evident
      * intent; see business-rules/CBACT01C.md BR-CBACT01C-05.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. COBDATFT.
       DATA DIVISION.
       LINKAGE SECTION.
       01  CO-REC.
           05  CO-INTYPE              PIC X.
           05  CO-INPDT               PIC X(20).
           05  CO-OUTTYPE             PIC X.
           05  CO-OUTDT               PIC X(20).
           05  CO-ERMSG               PIC X(38).
       PROCEDURE DIVISION USING CO-REC.
           EVALUATE CO-INTYPE
      *        VALIDIN1
               WHEN '1'
                   IF CO-INPDT(5:1) = '-' OR CO-OUTTYPE = '2'
                       MOVE 'INVALID INPUT' TO CO-ERMSG
                   ELSE
                       MOVE CO-INPDT(1:4) TO CO-OUTDT(1:4)
                       MOVE '-'           TO CO-OUTDT(5:1)
                       MOVE CO-INPDT(5:2) TO CO-OUTDT(6:2)
                       MOVE '-'           TO CO-OUTDT(8:1)
                       MOVE CO-INPDT(7:2) TO CO-OUTDT(9:2)
                   END-IF
      *        VALIDIN2 (the dash check is commented out in the source)
               WHEN '2'
                   IF CO-OUTTYPE = '1'
                       MOVE 'INVALID INPUT' TO CO-ERMSG
                   ELSE
                       MOVE CO-INPDT(1:4) TO CO-OUTDT(1:4)
                       MOVE CO-INPDT(6:2) TO CO-OUTDT(5:2)
                       MOVE CO-INPDT(9:2) TO CO-OUTDT(7:2)
                   END-IF
      *        GOTOERR
               WHEN OTHER
                   MOVE 'INVALID INPUT' TO CO-ERMSG
           END-EVALUATE
           MOVE 0 TO RETURN-CODE
           GOBACK.
