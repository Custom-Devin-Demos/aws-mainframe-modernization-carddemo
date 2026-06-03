package com.carddemo.interest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Card cross-reference entity (links a card number to a customer and account).
 *
 * <p>Traceability: COBOL copybook {@code app/cpy/CVACT03Y.cpy} (lines 4-8),
 * the {@code CARD-XREF-RECORD} structure (RECLN 50). Read by paragraph
 * {@code 1110-GET-XREF-DATA} of {@code app/cbl/CBACT04C.cbl} to obtain the card
 * number used when writing the interest transaction.
 *
 * <pre>
 * 05 XREF-CARD-NUM PIC X(16). -&gt; cardNumber (primary key in VSAM)
 * 05 XREF-CUST-ID  PIC 9(09). -&gt; customerId
 * 05 XREF-ACCT-ID  PIC 9(11). -&gt; accountId  (alternate key used for lookup)
 * </pre>
 */
@Entity
@Table(name = "card_xref")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardCrossReference {

    /** XREF-CARD-NUM PIC X(16). */
    @Id
    @Column(name = "card_number", length = 16)
    private String cardNumber;

    /** XREF-CUST-ID PIC 9(09). */
    @Column(name = "customer_id")
    private Long customerId;

    /** XREF-ACCT-ID PIC 9(11). */
    @Column(name = "account_id")
    private Long accountId;
}
