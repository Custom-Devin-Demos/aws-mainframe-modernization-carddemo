package com.carddemo.interest.service;

import com.carddemo.interest.repository.DisclosureGroupRepository;
import com.carddemo.interest.trace.Trace;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * 1200-GET-INTEREST-RATE / 1200-A-GET-DEFAULT-INT-RATE: look up DIS-INT-RATE by
 * (ACCT-GROUP-ID, TRANCAT-TYPE-CD, TRANCAT-CD); on status 23 (record not found) retry with
 * group id 'DEFAULT'.
 */
@Service
@Trace(program = "CBACT04C", paragraph = "1200-GET-INTEREST-RATE", lines = "415-440")
@Trace(program = "CBACT04C", paragraph = "1200-A-GET-DEFAULT-INT-RATE", lines = "443-460")
public class InterestRateService {

    private final DisclosureGroupRepository disclosureGroups;

    public InterestRateService(DisclosureGroupRepository disclosureGroups) {
        this.disclosureGroups = disclosureGroups;
    }

    /**
     * @return DIS-INT-RATE (scale 2) for the group, or for group 'DEFAULT' when the group is missing.
     * @throws InterestServiceException if neither record exists (COBOL abends, 9999-ABEND-PROGRAM)
     */
    public BigDecimal getRate(String groupId, String typeCd, String catCd) {
        throw new UnsupportedOperationException("Coordinator integration step 1");
    }
}
