package com.carddemo.intcalc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Disclosure-group lookup: paragraphs 1200-GET-INTEREST-RATE and 1200-A-GET-DEFAULT-INT-RATE. */
public final class InterestRateTable {

    public static final String DEFAULT_GROUP = "DEFAULT";

    private final Map<String, DisclosureGroup> byKey = new HashMap<>();

    public InterestRateTable(List<DisclosureGroup> groups) {
        for (DisclosureGroup g : groups) {
            if (byKey.putIfAbsent(g.key(), g) != null) {
                throw new IntCalcException("DUPLICATE DISCLOSURE GROUP KEY " + g.key());
            }
        }
    }

    /**
     * Returns DIS-INT-RATE for (group, type, category). A missing key (VSAM status '23') is
     * retried once with group 'DEFAULT'; a missing DEFAULT row abends. A row that exists with a
     * zero rate is a hit and does not fall back.
     */
    public BigDecimal rateFor(String groupId, String typeCode, String catCode) {
        DisclosureGroup hit = byKey.get(DisclosureGroup.key(groupId, typeCode, catCode));
        if (hit == null) {
            hit = byKey.get(DisclosureGroup.key(DEFAULT_GROUP, typeCode, catCode));
            if (hit == null) {
                throw new IntCalcException("ERROR READING DEFAULT DISCLOSURE GROUP: type "
                        + typeCode + " cat " + catCode);
            }
        }
        return hit.interestRate();
    }
}
