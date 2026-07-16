package com.nthippar.eventledger.account.service;

import com.nthippar.eventledger.account.api.AccountDetailsResponse;
import com.nthippar.eventledger.account.api.ApplyTransactionRequest;
import com.nthippar.eventledger.account.api.BalanceResponse;
import com.nthippar.eventledger.account.api.TransactionResponse;
import com.nthippar.eventledger.account.domain.AccountTransaction;
import com.nthippar.eventledger.account.domain.TransactionType;
import com.nthippar.eventledger.account.error.ConflictingTransactionException;
import com.nthippar.eventledger.account.mapper.AccountTransactionMapper;
import com.nthippar.eventledger.account.repository.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountTransactionRepository repository;
    private final AccountTransactionMapper mapper;
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public AccountService(
            AccountTransactionRepository repository,
            AccountTransactionMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public TransactionApplicationResult applyTransaction(
            String accountId,
            ApplyTransactionRequest request
    ) {
        log.info(
                "Applying transaction eventId={} accountId={}",
                request.eventId(),
                accountId
        );
        return repository.findById(request.eventId())
                .map(existing -> handleExistingTransaction(
                        accountId,
                        request,
                        existing
                ))
                .orElseGet(() -> persistNewTransaction(accountId, request));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        List<AccountTransaction> transactions =
                repository.findByAccountId(accountId);

        BigDecimal balance = calculateBalance(transactions);
        String currency = resolveCurrency(transactions);

        return new BalanceResponse(
                accountId,
                balance,
                currency
        );
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse getAccountDetails(String accountId) {
        List<AccountTransaction> transactions =
                repository.findByAccountIdOrderByEventTimestampDesc(accountId);

        BigDecimal balance = calculateBalance(transactions);
        String currency = resolveCurrency(transactions);

        List<TransactionResponse> recentTransactions = transactions.stream()
                .limit(10)
                .map(mapper::toResponse)
                .toList();

        return new AccountDetailsResponse(
                accountId,
                balance,
                currency,
                recentTransactions
        );
    }

    private TransactionApplicationResult handleExistingTransaction(
            String accountId,
            ApplyTransactionRequest request,
            AccountTransaction existing
    ) {
        boolean sameTransaction =
                existing.getAccountId().equals(accountId)
                        && existing.getType() == request.type()
                        && existing.getAmount()
                        .compareTo(request.amount()) == 0
                        && existing.getCurrency()
                        .equalsIgnoreCase(request.currency())
                        && existing.getEventTimestamp()
                        .equals(request.eventTimestamp());

        if (!sameTransaction) {
            throw new ConflictingTransactionException(request.eventId());
        }

        return new TransactionApplicationResult(
                mapper.toResponse(existing),
                false
        );
    }

    private TransactionApplicationResult persistNewTransaction(
            String accountId,
            ApplyTransactionRequest request
    ) {
        try {
            AccountTransaction savedTransaction =
                    repository.saveAndFlush(
                            mapper.toEntity(accountId, request)
                    );

            return new TransactionApplicationResult(
                    mapper.toResponse(savedTransaction),
                    true
            );
        } catch (DataIntegrityViolationException exception) {
            return repository.findById(request.eventId())
                    .map(existing -> handleExistingTransaction(
                            accountId,
                            request,
                            existing
                    ))
                    .orElseThrow(() -> exception);
        }
    }

    private BigDecimal calculateBalance(
            List<AccountTransaction> transactions
    ) {
        return transactions.stream()
                .map(transaction ->
                        transaction.getType() == TransactionType.CREDIT
                                ? transaction.getAmount()
                                : transaction.getAmount().negate()
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveCurrency(
            List<AccountTransaction> transactions
    ) {
        return transactions.stream()
                .findFirst()
                .map(AccountTransaction::getCurrency)
                .orElse("USD");
    }
}