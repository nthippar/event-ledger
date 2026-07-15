package com.nthippar.eventledger.event.repository;

import com.nthippar.eventledger.event.domain.LedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    List<LedgerEvent> findByAccountIdOrderByEventTimestampAsc(String accountId);
}