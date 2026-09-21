package com.carddemo.interest.repository;

import com.carddemo.interest.domain.DisclosureGroup;
import java.util.Optional;

/** Keyed access to DISCGRP-FILE (VSAM KSDS, key = DIS-GROUP-KEY). */
public interface DisclosureGroupRepository {
    Optional<DisclosureGroup> findByKey(String groupId, String typeCd, String catCd);
}
