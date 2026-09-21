      ******************************************************************
      * Program     : CBACT04C.CBL
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : This is a interest calculator program.
      ******************************************************************
      * Copyright Amazon.com, Inc. or its affiliates.
      * All Rights Reserved.
      *
      * Licensed under the Apache License, Version 2.0 (the "License").
      * You may not use this file except in compliance with the License.
      * You may obtain a copy of the License at
      *
      *    http://www.apache.org/licenses/LICENSE-2.0
      *
      * Unless required by applicable law or agreed to in writing,
      * software distributed under the License is distributed on an
      * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
      * either express or implied. See the License for the specific
      * language governing permissions and limitations under the License
      ******************************************************************
      * GnuCOBOL parity harness copy (java/interest-service/tools/cobol)
      * ---------------------------------------------------------------
      * This is app/cbl/CBACT04C.cbl with ONLY the VSAM KSDS access
      * replaced by LINE SEQUENTIAL files + in-memory keyed lookups,
      * because the GnuCOBOL build on the dev box has no indexed-file
      * handler.  Every change is tagged with "*LS" in columns 1-7 or
      * listed in tools/cobol/README.md.  Paragraphs 1300-COMPUTE-
      * INTEREST and 1300-B-WRITE-TX are byte-identical to the original;
      * 1050 / 1200 / 1200-A differ only in the single I/O verb, which
      * is delegated to an LS-* paragraph that reproduces the same
      * FILE STATUS values ('00' found, '23' not found).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBACT04C.
       AUTHOR.        AWS.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
      *LS  INDEXED/SEQUENTIAL -> LINE SEQUENTIAL (one record per line).
      *LS  TCATBALF must already be in ascending key order (KSDS order).
           SELECT TCATBAL-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS TCATBALF-STATUS.

           SELECT XREF-FILE ASSIGN TO   XREFFILE
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS XREFFILE-STATUS.

           SELECT ACCOUNT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS ACCTFILE-STATUS.

      *LS  New: updated account records are written here on close
      *LS  (replaces the in-place KSDS REWRITE).
           SELECT ACCTOUT-FILE ASSIGN TO ACCTOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS ACCTOUT-STATUS.

           SELECT DISCGRP-FILE ASSIGN TO DISCGRP
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS DISCGRP-STATUS.

           SELECT TRANSACT-FILE ASSIGN TO TRANSACT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS TRANFILE-STATUS.

      *
       DATA DIVISION.
       FILE SECTION.
       FD  TCATBAL-FILE.
       01  FD-TRAN-CAT-BAL-RECORD.
           05 FD-TRAN-CAT-KEY.
              10 FD-TRANCAT-ACCT-ID             PIC 9(11).
              10 FD-TRANCAT-TYPE-CD             PIC X(02).
              10 FD-TRANCAT-CD                  PIC 9(04).
           05 FD-FD-TRAN-CAT-DATA               PIC X(33).

       FD  XREF-FILE.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM                  PIC X(16).
           05 FD-XREF-CUST-NUM                  PIC 9(09).
           05 FD-XREF-ACCT-ID                   PIC 9(11).
           05 FD-XREF-FILLER                    PIC X(14).

       FD  DISCGRP-FILE.
       01  FD-DISCGRP-REC.
           05 FD-DISCGRP-KEY.
              10 FD-DIS-ACCT-GROUP-ID           PIC X(10).
              10 FD-DIS-TRAN-TYPE-CD            PIC X(02).
              10 FD-DIS-TRAN-CAT-CD             PIC 9(04).
           05 FD-DISCGRP-DATA                   PIC X(34).

       FD  ACCOUNT-FILE.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).

      *LS  New output FD, same layout as ACCOUNT-FILE.
       FD  ACCTOUT-FILE.
       01  FD-ACCTOUT-REC                       PIC X(300).

       FD  TRANSACT-FILE.
       01  FD-TRANFILE-REC.
           05 FD-TRANS-ID                       PIC X(16).
           05 FD-ACCT-DATA                      PIC X(334).

       WORKING-STORAGE SECTION.

      *****************************************************************
       COPY CVTRA01Y.
       01  TCATBALF-STATUS.
           05  TCATBALF-STAT1      PIC X.
           05  TCATBALF-STAT2      PIC X.

       COPY CVACT03Y.
       01  XREFFILE-STATUS.
           05  XREFFILE-STAT1      PIC X.
           05  XREFFILE-STAT2      PIC X.

       COPY CVTRA02Y.
       01  DISCGRP-STATUS.
           05 DISCGRP-STAT1        PIC X.
           05 DISCGRP-STAT2        PIC X.

       COPY CVACT01Y.
       01  ACCTFILE-STATUS.
           05  ACCTFILE-STAT1      PIC X.
           05  ACCTFILE-STAT2      PIC X.

       COPY CVTRA05Y.
       01  TRANFILE-STATUS.
           05  TRANFILE-STAT1      PIC X.
           05  TRANFILE-STAT2      PIC X.

       01  IO-STATUS.
           05  IO-STAT1            PIC X.
           05  IO-STAT2            PIC X.
       01  TWO-BYTES-BINARY        PIC 9(4) BINARY.
       01  TWO-BYTES-ALPHA         REDEFINES TWO-BYTES-BINARY.
           05  TWO-BYTES-LEFT      PIC X.
           05  TWO-BYTES-RIGHT     PIC X.
       01  IO-STATUS-04.
           05  IO-STATUS-0401      PIC 9   VALUE 0.
           05  IO-STATUS-0403      PIC 999 VALUE 0.

       01  APPL-RESULT             PIC S9(9)   COMP.
           88  APPL-AOK            VALUE 0.
           88  APPL-EOF            VALUE 16.

       01  END-OF-FILE             PIC X(01)    VALUE 'N'.
       01  ABCODE                  PIC S9(9) BINARY.
       01  TIMING                  PIC S9(9) BINARY.
      * T I M E S T A M P   D B 2  X(26)     EEEE-MM-DD-UU.MM.SS.HH0000
       01  COBOL-TS.
           05 COB-YYYY                  PIC X(04).
           05 COB-MM                    PIC X(02).
           05 COB-DD                    PIC X(02).
           05 COB-HH                    PIC X(02).
           05 COB-MIN                   PIC X(02).
           05 COB-SS                    PIC X(02).
           05 COB-MIL                   PIC X(02).
           05 COB-REST                  PIC X(05).
       01  DB2-FORMAT-TS                PIC X(26).
       01  FILLER REDEFINES DB2-FORMAT-TS.
           06 DB2-YYYY                  PIC X(004).                      E
           06 DB2-STREEP-1              PIC X.                           -
           06 DB2-MM                    PIC X(002).                      M
           06 DB2-STREEP-2              PIC X.                           -
           06 DB2-DD                    PIC X(002).                      D
           06 DB2-STREEP-3              PIC X.                           -
           06 DB2-HH                    PIC X(002).                      U
           06 DB2-DOT-1                 PIC X.
           06 DB2-MIN                   PIC X(002).
           06 DB2-DOT-2                 PIC X.
           06 DB2-SS                    PIC X(002).
           06 DB2-DOT-3                 PIC X.
           06 DB2-MIL                   PIC 9(002).
           06 DB2-REST                  PIC X(04).
       01 WS-MISC-VARS.
           05 WS-LAST-ACCT-NUM          PIC X(11) VALUE SPACES.
           05 WS-MONTHLY-INT            PIC S9(09)V99.
           05 WS-TOTAL-INT              PIC S9(09)V99.
           05 WS-FIRST-TIME             PIC X(01) VALUE 'Y'.
       01 WS-COUNTERS.
           05 WS-RECORD-COUNT           PIC 9(09) VALUE 0.
           05 WS-TRANID-SUFFIX          PIC 9(06) VALUE 0.

      *LS  In-memory images of the three keyed VSAM files.
       01  ACCTOUT-STATUS.
           05  ACCTOUT-STAT1       PIC X.
           05  ACCTOUT-STAT2       PIC X.
       01  LS-EOF                  PIC X VALUE 'N'.
       01  LS-IDX                  PIC 9(05) COMP.
       01  LS-XREF-COUNT           PIC 9(05) COMP VALUE 0.
       01  LS-XREF-TABLE.
           05  LS-XREF-ENTRY OCCURS 20000 TIMES.
               10  LS-XREF-CARD-NUM     PIC X(16).
               10  LS-XREF-CUST-NUM     PIC 9(09).
               10  LS-XREF-ACCT-ID      PIC 9(11).
               10  LS-XREF-FILLER       PIC X(14).
       01  LS-ACCT-COUNT           PIC 9(05) COMP VALUE 0.
       01  LS-ACCT-CUR             PIC 9(05) COMP VALUE 0.
       01  LS-ACCT-TABLE.
           05  LS-ACCT-ENTRY OCCURS 20000 TIMES.
               10  LS-ACCT-ID           PIC 9(11).
               10  LS-ACCT-DATA         PIC X(289).
       01  LS-DISC-COUNT           PIC 9(05) COMP VALUE 0.
       01  LS-DISC-TABLE.
           05  LS-DISC-ENTRY OCCURS 20000 TIMES.
               10  LS-DISC-KEY          PIC X(16).
               10  LS-DISC-DATA         PIC X(34).

       LINKAGE SECTION.
       01  EXTERNAL-PARMS.
           05  PARM-LENGTH         PIC S9(04) COMP.
           05  PARM-DATE           PIC X(10).
      *****************************************************************
       PROCEDURE DIVISION USING EXTERNAL-PARMS.
           DISPLAY 'START OF EXECUTION OF PROGRAM CBACT04C'.
           PERFORM 0000-TCATBALF-OPEN.
           PERFORM 0100-XREFFILE-OPEN.
           PERFORM 0200-DISCGRP-OPEN.
           PERFORM 0300-ACCTFILE-OPEN.
           PERFORM 0400-TRANFILE-OPEN.

           PERFORM UNTIL END-OF-FILE = 'Y'
               IF  END-OF-FILE = 'N'
                   PERFORM 1000-TCATBALF-GET-NEXT
                   IF  END-OF-FILE = 'N'
                     ADD 1 TO WS-RECORD-COUNT
                     DISPLAY TRAN-CAT-BAL-RECORD
                     IF TRANCAT-ACCT-ID NOT= WS-LAST-ACCT-NUM
                       IF WS-FIRST-TIME NOT = 'Y'
                          PERFORM 1050-UPDATE-ACCOUNT
                       ELSE
                          MOVE 'N' TO WS-FIRST-TIME
                       END-IF
                       MOVE 0 TO WS-TOTAL-INT
                       MOVE TRANCAT-ACCT-ID TO WS-LAST-ACCT-NUM
                       MOVE TRANCAT-ACCT-ID TO FD-ACCT-ID
                       PERFORM 1100-GET-ACCT-DATA
                       MOVE TRANCAT-ACCT-ID TO FD-XREF-ACCT-ID
                       PERFORM 1110-GET-XREF-DATA
                     END-IF
      *              DISPLAY 'ACCT-GROUP-ID: ' ACCT-GROUP-ID
      *              DISPLAY 'TRANCAT-CD: ' TRANCAT-CD
      *              DISPLAY 'TRANCAT-TYPE-CD: ' TRANCAT-TYPE-CD
                     MOVE ACCT-GROUP-ID TO FD-DIS-ACCT-GROUP-ID
                     MOVE TRANCAT-CD TO FD-DIS-TRAN-CAT-CD
                     MOVE TRANCAT-TYPE-CD TO FD-DIS-TRAN-TYPE-CD
                     PERFORM 1200-GET-INTEREST-RATE
                     IF DIS-INT-RATE NOT = 0
                       PERFORM 1300-COMPUTE-INTEREST
                       PERFORM 1400-COMPUTE-FEES
                     END-IF
                   END-IF
               ELSE
                    PERFORM 1050-UPDATE-ACCOUNT
               END-IF
           END-PERFORM.

           PERFORM 9000-TCATBALF-CLOSE.
           PERFORM 9100-XREFFILE-CLOSE.
           PERFORM 9200-DISCGRP-CLOSE.
           PERFORM 9300-ACCTFILE-CLOSE.
           PERFORM 9400-TRANFILE-CLOSE.

           DISPLAY 'END OF EXECUTION OF PROGRAM CBACT04C'.

           GOBACK.
      *---------------------------------------------------------------*
       0000-TCATBALF-OPEN.
           MOVE 8 TO APPL-RESULT.
           OPEN INPUT TCATBAL-FILE
           IF  TCATBALF-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING TRANSACTION CATEGORY BALANCE'
               MOVE TCATBALF-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       0100-XREFFILE-OPEN.
           MOVE 8 TO APPL-RESULT.
           OPEN INPUT XREF-FILE
      *LS  load the whole file into LS-XREF-TABLE
           IF  XREFFILE-STATUS = '00'
               PERFORM LS-LOAD-XREF
           END-IF
           IF  XREFFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING CROSS REF FILE'   XREFFILE-STATUS
               MOVE XREFFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       0200-DISCGRP-OPEN.
           MOVE 8 TO APPL-RESULT.
           OPEN INPUT DISCGRP-FILE
      *LS  load the whole file into LS-DISC-TABLE
           IF  DISCGRP-STATUS = '00'
               PERFORM LS-LOAD-DISCGRP
           END-IF
           IF  DISCGRP-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING DALY REJECTS FILE'
               MOVE DISCGRP-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       0300-ACCTFILE-OPEN.
           MOVE 8 TO APPL-RESULT.
      *LS  I-O -> INPUT + load into LS-ACCT-TABLE; open ACCTOUT-FILE
           OPEN INPUT ACCOUNT-FILE
           IF  ACCTFILE-STATUS = '00'
               PERFORM LS-LOAD-ACCOUNTS
           END-IF
           IF  ACCTFILE-STATUS = '00'
               OPEN OUTPUT ACCTOUT-FILE
               MOVE ACCTOUT-STATUS TO ACCTFILE-STATUS
           END-IF
           IF  ACCTFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING ACCOUNT MASTER FILE'
               MOVE ACCTFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       0400-TRANFILE-OPEN.
           MOVE 8 TO APPL-RESULT.
           OPEN OUTPUT TRANSACT-FILE
           IF  TRANFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING TRANSACTION FILE'
               MOVE TRANFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       1000-TCATBALF-GET-NEXT.
           READ TCATBAL-FILE INTO TRAN-CAT-BAL-RECORD.
           IF  TCATBALF-STATUS  = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               IF  TCATBALF-STATUS  = '10'
                   MOVE 16 TO APPL-RESULT
               ELSE
                   MOVE 12 TO APPL-RESULT
               END-IF
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               IF  APPL-EOF
                   MOVE 'Y' TO END-OF-FILE
               ELSE
                   DISPLAY 'ERROR READING TRANSACTION CATEGORY FILE'
                   MOVE TCATBALF-STATUS TO IO-STATUS
                   PERFORM 9910-DISPLAY-IO-STATUS
                   PERFORM 9999-ABEND-PROGRAM
               END-IF
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       1050-UPDATE-ACCOUNT.
      * Update the balances in account record to reflect posted trans.
           ADD WS-TOTAL-INT  TO ACCT-CURR-BAL
           MOVE 0 TO ACCT-CURR-CYC-CREDIT
           MOVE 0 TO ACCT-CURR-CYC-DEBIT

      *LS  was: REWRITE FD-ACCTFILE-REC FROM  ACCOUNT-RECORD
           PERFORM LS-REWRITE-ACCOUNT
           IF  ACCTFILE-STATUS  = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR RE-WRITING ACCOUNT FILE'
               MOVE ACCTFILE-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       1100-GET-ACCT-DATA.
      *LS  was: READ ACCOUNT-FILE INTO ACCOUNT-RECORD INVALID KEY ...
           PERFORM LS-READ-ACCOUNT
           IF  ACCTFILE-STATUS = '23'
                  DISPLAY 'ACCOUNT NOT FOUND: ' FD-ACCT-ID
           END-IF

           IF  ACCTFILE-STATUS  = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR READING ACCOUNT FILE'
               MOVE ACCTFILE-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       1110-GET-XREF-DATA.
      *LS  was: READ XREF-FILE INTO CARD-XREF-RECORD
      *LS         KEY IS FD-XREF-ACCT-ID INVALID KEY ...
           PERFORM LS-READ-XREF
           IF  XREFFILE-STATUS = '23'
                  DISPLAY 'ACCOUNT NOT FOUND: ' FD-XREF-ACCT-ID
           END-IF

           IF  XREFFILE-STATUS   = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR READING XREF FILE'
               MOVE XREFFILE-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       1200-GET-INTEREST-RATE.
      *LS  was: READ DISCGRP-FILE INTO DIS-GROUP-RECORD INVALID KEY ...
           PERFORM LS-READ-DISCGRP
           IF  DISCGRP-STATUS = '23'
                   DISPLAY 'DISCLOSURE GROUP RECORD MISSING'
                   DISPLAY 'TRY WITH DEFAULT GROUP CODE'
           END-IF.

           IF  DISCGRP-STATUS  = '00'  OR '23'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF

           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR READING DISCLOSURE GROUP FILE'
               MOVE DISCGRP-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           IF  DISCGRP-STATUS  = '23'
               MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID
               PERFORM 1200-A-GET-DEFAULT-INT-RATE
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1200-A-GET-DEFAULT-INT-RATE.
      *LS  was: READ DISCGRP-FILE INTO DIS-GROUP-RECORD
           PERFORM LS-READ-DISCGRP

           IF  DISCGRP-STATUS  = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF

           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR READING DEFAULT DISCLOSURE GROUP'
               MOVE DISCGRP-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       1300-COMPUTE-INTEREST.

           COMPUTE WS-MONTHLY-INT
            = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200

           ADD WS-MONTHLY-INT  TO WS-TOTAL-INT
           PERFORM 1300-B-WRITE-TX.

           EXIT.

      *---------------------------------------------------------------*
       1300-B-WRITE-TX.
           ADD 1 TO WS-TRANID-SUFFIX

           STRING PARM-DATE,
                  WS-TRANID-SUFFIX
             DELIMITED BY SIZE
             INTO TRAN-ID
           END-STRING.

           MOVE '01'                 TO TRAN-TYPE-CD
           MOVE '05'                 TO TRAN-CAT-CD
           MOVE 'System'             TO TRAN-SOURCE
           STRING 'Int. for a/c ' ,
                  ACCT-ID
                  DELIMITED BY SIZE
            INTO TRAN-DESC
           END-STRING
           MOVE WS-MONTHLY-INT       TO TRAN-AMT
           MOVE 0                    TO TRAN-MERCHANT-ID
           MOVE SPACES               TO TRAN-MERCHANT-NAME
           MOVE SPACES               TO TRAN-MERCHANT-CITY
           MOVE SPACES               TO TRAN-MERCHANT-ZIP
           MOVE XREF-CARD-NUM        TO TRAN-CARD-NUM
           PERFORM Z-GET-DB2-FORMAT-TIMESTAMP
           MOVE DB2-FORMAT-TS        TO TRAN-ORIG-TS
           MOVE DB2-FORMAT-TS        TO TRAN-PROC-TS

           WRITE FD-TRANFILE-REC FROM TRAN-RECORD
           IF  TRANFILE-STATUS   = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF

           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR WRITING TRANSACTION RECORD'
               MOVE TRANFILE-STATUS   TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1400-COMPUTE-FEES.
      * To be implemented
           EXIT.
      *---------------------------------------------------------------*
       9000-TCATBALF-CLOSE.
           MOVE 8 TO  APPL-RESULT.
           CLOSE TCATBAL-FILE
           IF  TCATBALF-STATUS = '00'
               MOVE 0 TO  APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING TRANSACTION BALANCE FILE'
               MOVE TCATBALF-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       9100-XREFFILE-CLOSE.
           MOVE 8 TO APPL-RESULT.
           CLOSE XREF-FILE
           IF  XREFFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING CROSS REF FILE'
               MOVE XREFFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       9200-DISCGRP-CLOSE.
           MOVE 8 TO APPL-RESULT.
           CLOSE DISCGRP-FILE
           IF  DISCGRP-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING DISCLOSURE GROUP FILE'
               MOVE DISCGRP-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.
      *---------------------------------------------------------------*
       9300-ACCTFILE-CLOSE.
           MOVE 8 TO APPL-RESULT.
      *LS  dump LS-ACCT-TABLE (input order) to ACCTOUT-FILE first
           PERFORM LS-UNLOAD-ACCOUNTS
           CLOSE ACCOUNT-FILE
           IF  ACCTFILE-STATUS  = '00'
               MOVE ACCTOUT-STATUS TO ACCTFILE-STATUS
           END-IF
           IF  ACCTFILE-STATUS  = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING ACCOUNT FILE'
               MOVE ACCTFILE-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

       9400-TRANFILE-CLOSE.
           MOVE 8 TO APPL-RESULT.
           CLOSE TRANSACT-FILE
           IF  TRANFILE-STATUS  = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF  APPL-AOK
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING TRANSACTION FILE'
               MOVE TRANFILE-STATUS  TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

       Z-GET-DB2-FORMAT-TIMESTAMP.
           MOVE FUNCTION CURRENT-DATE TO COBOL-TS
           MOVE COB-YYYY TO DB2-YYYY
           MOVE COB-MM   TO DB2-MM
           MOVE COB-DD   TO DB2-DD
           MOVE COB-HH   TO DB2-HH
           MOVE COB-MIN  TO DB2-MIN
           MOVE COB-SS   TO DB2-SS
           MOVE COB-MIL  TO DB2-MIL
           MOVE '0000'   TO DB2-REST
           MOVE '-' TO DB2-STREEP-1 DB2-STREEP-2 DB2-STREEP-3
           MOVE '.' TO DB2-DOT-1 DB2-DOT-2 DB2-DOT-3
      *    DISPLAY 'DB2-TIMESTAMP = ' DB2-FORMAT-TS
           EXIT.

       9999-ABEND-PROGRAM.
           DISPLAY 'ABENDING PROGRAM'
           MOVE 0 TO TIMING
           MOVE 999 TO ABCODE
           CALL 'CEE3ABD' USING ABCODE, TIMING.

      *LS *************************************************************
      *LS  In-memory emulation of the VSAM KSDS accesses.
      *LS  Each LS-READ-* sets the file's FILE STATUS to '00' when the
      *LS  key is found and '23' (record not found) otherwise, exactly
      *LS  the codes the original program tests for.
      *LS *************************************************************
       LS-LOAD-XREF.
           MOVE 'N' TO LS-EOF
           PERFORM UNTIL LS-EOF = 'Y'
               READ XREF-FILE
                   AT END MOVE 'Y' TO LS-EOF
                   NOT AT END
                       ADD 1 TO LS-XREF-COUNT
                       MOVE FD-XREFFILE-REC
                         TO LS-XREF-ENTRY(LS-XREF-COUNT)
               END-READ
           END-PERFORM
           MOVE '00' TO XREFFILE-STATUS
           EXIT.

       LS-LOAD-DISCGRP.
           MOVE 'N' TO LS-EOF
           PERFORM UNTIL LS-EOF = 'Y'
               READ DISCGRP-FILE
                   AT END MOVE 'Y' TO LS-EOF
                   NOT AT END
                       ADD 1 TO LS-DISC-COUNT
                       MOVE FD-DISCGRP-REC
                         TO LS-DISC-ENTRY(LS-DISC-COUNT)
               END-READ
           END-PERFORM
           MOVE '00' TO DISCGRP-STATUS
           EXIT.

       LS-LOAD-ACCOUNTS.
           MOVE 'N' TO LS-EOF
           PERFORM UNTIL LS-EOF = 'Y'
               READ ACCOUNT-FILE
                   AT END MOVE 'Y' TO LS-EOF
                   NOT AT END
                       ADD 1 TO LS-ACCT-COUNT
                       MOVE FD-ACCTFILE-REC
                         TO LS-ACCT-ENTRY(LS-ACCT-COUNT)
               END-READ
           END-PERFORM
           MOVE '00' TO ACCTFILE-STATUS
           EXIT.

      *LS  READ XREF-FILE KEY IS FD-XREF-ACCT-ID (alternate key, first
      *LS  match in file order).
       LS-READ-XREF.
           MOVE '23' TO XREFFILE-STATUS
           PERFORM VARYING LS-IDX FROM 1 BY 1
             UNTIL LS-IDX > LS-XREF-COUNT
                OR XREFFILE-STATUS = '00'
               IF LS-XREF-ACCT-ID(LS-IDX) = FD-XREF-ACCT-ID
                   MOVE LS-XREF-ENTRY(LS-IDX) TO FD-XREFFILE-REC
                   MOVE FD-XREFFILE-REC       TO CARD-XREF-RECORD
                   MOVE '00' TO XREFFILE-STATUS
               END-IF
           END-PERFORM
           EXIT.

      *LS  READ ACCOUNT-FILE (primary key FD-ACCT-ID). Remembers the
      *LS  slot so LS-REWRITE-ACCOUNT can update it in place.
       LS-READ-ACCOUNT.
           MOVE '23' TO ACCTFILE-STATUS
           MOVE 0 TO LS-ACCT-CUR
           PERFORM VARYING LS-IDX FROM 1 BY 1
             UNTIL LS-IDX > LS-ACCT-COUNT
                OR ACCTFILE-STATUS = '00'
               IF LS-ACCT-ID(LS-IDX) = FD-ACCT-ID
                   MOVE LS-IDX TO LS-ACCT-CUR
                   MOVE LS-ACCT-ENTRY(LS-IDX) TO FD-ACCTFILE-REC
                   MOVE FD-ACCTFILE-REC       TO ACCOUNT-RECORD
                   MOVE '00' TO ACCTFILE-STATUS
               END-IF
           END-PERFORM
           EXIT.

      *LS  REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD
       LS-REWRITE-ACCOUNT.
           IF LS-ACCT-CUR > 0
               MOVE ACCOUNT-RECORD TO FD-ACCTFILE-REC
               MOVE FD-ACCTFILE-REC TO LS-ACCT-ENTRY(LS-ACCT-CUR)
               MOVE '00' TO ACCTFILE-STATUS
           ELSE
               MOVE '43' TO ACCTFILE-STATUS
           END-IF
           EXIT.

      *LS  READ DISCGRP-FILE (primary key FD-DISCGRP-KEY).
       LS-READ-DISCGRP.
           MOVE '23' TO DISCGRP-STATUS
           PERFORM VARYING LS-IDX FROM 1 BY 1
             UNTIL LS-IDX > LS-DISC-COUNT
                OR DISCGRP-STATUS = '00'
               IF LS-DISC-KEY(LS-IDX) = FD-DISCGRP-KEY
                   MOVE LS-DISC-ENTRY(LS-IDX) TO FD-DISCGRP-REC
                   MOVE FD-DISCGRP-REC        TO DIS-GROUP-RECORD
                   MOVE '00' TO DISCGRP-STATUS
               END-IF
           END-PERFORM
           EXIT.

      *LS  Write every account (input order) to ACCTOUT-FILE and close.
       LS-UNLOAD-ACCOUNTS.
           PERFORM VARYING LS-IDX FROM 1 BY 1
             UNTIL LS-IDX > LS-ACCT-COUNT
               WRITE FD-ACCTOUT-REC FROM LS-ACCT-ENTRY(LS-IDX)
           END-PERFORM
           CLOSE ACCTOUT-FILE
           EXIT.

      *****************************************************************
       9910-DISPLAY-IO-STATUS.
           IF  IO-STATUS NOT NUMERIC
           OR  IO-STAT1 = '9'
               MOVE IO-STAT1 TO IO-STATUS-04(1:1)
               MOVE 0        TO TWO-BYTES-BINARY
               MOVE IO-STAT2 TO TWO-BYTES-RIGHT
               MOVE TWO-BYTES-BINARY TO IO-STATUS-0403
               DISPLAY 'FILE STATUS IS: NNNN' IO-STATUS-04
           ELSE
               MOVE '0000' TO IO-STATUS-04
               MOVE IO-STATUS TO IO-STATUS-04(3:2)
               DISPLAY 'FILE STATUS IS: NNNN' IO-STATUS-04
           END-IF
           EXIT.

      *
      * Ver: CardDemo_v2.0-25-gdb72e6b-235 Date: 2025-04-29 11:01:28 CDT
      *
