package com.carddemo.interest.repository;

import com.carddemo.interest.model.DisclosureGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link DisclosureGroup}.
 *
 * <p>Mirrors the random-access {@code DISCGRP-FILE} (DISCGRP) read in
 * {@code app/cbl/CBACT04C.cbl} paragraph {@code 1200-GET-INTEREST-RATE}
 * (lines 415-440). A missing record corresponds to COBOL file status '23'
 * (record not found), which the service handles via the DEFAULT-group fallback.
 */
@Repository
public interface DisclosureGroupRepository
        extends JpaRepository<DisclosureGroup, DisclosureGroup.Key> {

    /**
     * Looks up the disclosure group row by its full composite key.
     *
     * <p>The COBOL key fields are {@code DIS-TRAN-TYPE-CD} and
     * {@code DIS-TRAN-CAT-CD}; this method exposes them with the shorter
     * {@code typeCode}/{@code categoryCode} names used throughout the service,
     * mapping to the entity's {@code transactionTypeCode}/
     * {@code transactionCategoryCode} properties via an explicit query.
     *
     * @param groupId      account group id (DIS-ACCT-GROUP-ID)
     * @param typeCode     transaction type code (DIS-TRAN-TYPE-CD)
     * @param categoryCode transaction category code (DIS-TRAN-CAT-CD)
     * @return the matching disclosure group, or empty if not found (status '23')
     */
    @Query("SELECT d FROM DisclosureGroup d WHERE d.groupId = :groupId "
            + "AND d.transactionTypeCode = :typeCode "
            + "AND d.transactionCategoryCode = :categoryCode")
    Optional<DisclosureGroup> findByGroupIdAndTypeCodeAndCategoryCode(
            @Param("groupId") String groupId,
            @Param("typeCode") String typeCode,
            @Param("categoryCode") int categoryCode);
}
