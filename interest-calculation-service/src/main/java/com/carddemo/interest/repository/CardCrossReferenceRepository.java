package com.carddemo.interest.repository;

import com.carddemo.interest.model.CardCrossReference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link CardCrossReference}.
 *
 * <p>Mirrors the alternate-key (FD-XREF-ACCT-ID) read of the {@code XREF-FILE}
 * (XREFFILE) in {@code app/cbl/CBACT04C.cbl} paragraph
 * {@code 1110-GET-XREF-DATA} (lines 393-413).
 */
@Repository
public interface CardCrossReferenceRepository extends JpaRepository<CardCrossReference, String> {

    /**
     * Finds the cross-reference for an account via the account-id alternate key.
     */
    Optional<CardCrossReference> findByAccountId(Long accountId);
}
