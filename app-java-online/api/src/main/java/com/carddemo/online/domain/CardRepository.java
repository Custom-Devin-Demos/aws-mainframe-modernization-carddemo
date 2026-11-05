package com.carddemo.online.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, String> {

    List<Card> findByAcctIdOrderByCardNumAsc(Long acctId);
}
