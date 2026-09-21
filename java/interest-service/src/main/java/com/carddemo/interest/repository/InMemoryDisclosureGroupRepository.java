package com.carddemo.interest.repository;

import com.carddemo.interest.domain.DisclosureGroup;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Map-backed DISCGRP-FILE keyed by DIS-GROUP-KEY (groupId + typeCd + catCd). */
@Repository
public class InMemoryDisclosureGroupRepository implements DisclosureGroupRepository {

    private final Map<String, DisclosureGroup> byKey = new LinkedHashMap<>();

    public static String key(String groupId, String typeCd, String catCd) {
        return groupId + "|" + typeCd + "|" + catCd;
    }

    public void load(Collection<DisclosureGroup> groups) {
        byKey.clear();
        groups.forEach(g -> byKey.put(key(g.groupId(), g.typeCd(), g.catCd()), g));
    }

    @Override
    public Optional<DisclosureGroup> findByKey(String groupId, String typeCd, String catCd) {
        return Optional.ofNullable(byKey.get(key(groupId, typeCd, catCd)));
    }
}
