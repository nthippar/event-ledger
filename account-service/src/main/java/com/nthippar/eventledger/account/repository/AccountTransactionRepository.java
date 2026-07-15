package com.nthippar.eventledger.account.repository;

import com.nthippar.eventledger.account.domain.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, String> {

    List<AccountTransaction>
    findByAccountIdOrderByEventTimestampDesc(String accountId);

    List<AccountTransaction>
    findByAccountId(String accountId);
}