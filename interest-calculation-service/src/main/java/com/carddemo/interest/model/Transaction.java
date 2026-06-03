package com.carddemo.interest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction entity (the interest transaction written by the batch program).
 *
 * <p>Traceability: COBOL copybook {@code app/cpy/CVTRA05Y.cpy} (lines 4-18),
 * the {@code TRAN-RECORD} structure (RECLN 350). Populated and written by
 * paragraph {@code 1300-B-WRITE-TX} of {@code app/cbl/CBACT04C.cbl}.
 *
 * <pre>
 * 05 TRAN-ID            PIC X(16).      -&gt; transactionId
 * 05 TRAN-TYPE-CD       PIC X(02).      -&gt; typeCode
 * 05 TRAN-CAT-CD        PIC 9(04).      -&gt; categoryCode
 * 05 TRAN-SOURCE        PIC X(10).      -&gt; source
 * 05 TRAN-DESC          PIC X(100).     -&gt; description
 * 05 TRAN-AMT           PIC S9(09)V99.  -&gt; amount
 * 05 TRAN-MERCHANT-ID   PIC 9(09).      -&gt; merchantId
 * 05 TRAN-MERCHANT-NAME PIC X(50).      -&gt; merchantName
 * 05 TRAN-MERCHANT-CITY PIC X(50).      -&gt; merchantCity
 * 05 TRAN-MERCHANT-ZIP  PIC X(10).      -&gt; merchantZip
 * 05 TRAN-CARD-NUM      PIC X(16).      -&gt; cardNumber
 * 05 TRAN-ORIG-TS       PIC X(26).      -&gt; originTimestamp
 * 05 TRAN-PROC-TS       PIC X(26).      -&gt; processTimestamp
 * </pre>
 */
@Entity
@Table(name = "transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    /** TRAN-ID PIC X(16). */
    @Id
    @Column(name = "transaction_id", length = 16)
    private String transactionId;

    /** TRAN-TYPE-CD PIC X(02). */
    @Column(name = "type_code", length = 2)
    private String typeCode;

    /** TRAN-CAT-CD PIC 9(04). */
    @Column(name = "category_code")
    private int categoryCode;

    /** TRAN-SOURCE PIC X(10). */
    @Column(name = "source", length = 10)
    private String source;

    /** TRAN-DESC PIC X(100). */
    @Column(name = "description", length = 100)
    private String description;

    /** TRAN-AMT PIC S9(09)V99. */
    @Column(name = "amount", precision = 11, scale = 2)
    private BigDecimal amount;

    /** TRAN-MERCHANT-ID PIC 9(09). */
    @Column(name = "merchant_id")
    private Long merchantId;

    /** TRAN-MERCHANT-NAME PIC X(50). */
    @Column(name = "merchant_name", length = 50)
    private String merchantName;

    /** TRAN-MERCHANT-CITY PIC X(50). */
    @Column(name = "merchant_city", length = 50)
    private String merchantCity;

    /** TRAN-MERCHANT-ZIP PIC X(10). */
    @Column(name = "merchant_zip", length = 10)
    private String merchantZip;

    /** TRAN-CARD-NUM PIC X(16). */
    @Column(name = "card_number", length = 16)
    private String cardNumber;

    /** TRAN-ORIG-TS PIC X(26). */
    @Column(name = "origin_timestamp", length = 26)
    private String originTimestamp;

    /** TRAN-PROC-TS PIC X(26). */
    @Column(name = "process_timestamp", length = 26)
    private String processTimestamp;
}
