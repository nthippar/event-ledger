package com.nthippar.eventledger.account.service;

import com.nthippar.eventledger.account.api.AccountDetailsResponse;
import com.nthippar.eventledger.account.api.ApplyTransactionRequest;
import com.nthippar.eventledger.account.api.BalanceResponse;
import com.nthippar.eventledger.account.api.TransactionResponse;
import com.nthippar.eventledger.account.domain.AccountTransaction;
import com.nthippar.eventledger.account.domain.TransactionType;
import com.nthippar.eventledger.account.mapper.AccountTransactionMapper;
import com.nthippar.eventledger.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountTransactionRepository repository;

    @Mock
    private AccountTransactionMapper mapper;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(repository, mapper);
    }

    @Test
    void shouldPersistNewTransaction() {
        ApplyTransactionRequest request = request(
                "evt-001",
                TransactionType.CREDIT,
                "150.00",
                "2026-05-15T14:02:11Z"
        );

        AccountTransaction entity = transaction(
                "evt-001",
                TransactionType.CREDIT,
                "150.00",
                "2026-05-15T14:02:11Z"
        );

        TransactionResponse response = response(entity);

        when(repository.findById("evt-001"))
                .thenReturn(Optional.empty());
        when(mapper.toEntity("acct-123", request))
                .thenReturn(entity);
        when(repository.saveAndFlush(entity))
                .thenReturn(entity);
        when(mapper.toResponse(entity))
                .thenReturn(response);

        TransactionApplicationResult result =
                service.applyTransaction("acct-123", request);

        assertThat(result.created()).isTrue();
        assertThat(result.transaction()).isEqualTo(response);

        verify(repository).saveAndFlush(entity);
    }

    @Test
    void shouldReturnExistingTransactionWithoutSavingDuplicate() {
        ApplyTransactionRequest request = request(
                "evt-duplicate",
                TransactionType.CREDIT,
                "150.00",
                "2026-05-15T14:02:11Z"
        );

        AccountTransaction existing = transaction(
                "evt-duplicate",
                TransactionType.CREDIT,
                "150.00",
                "2026-05-15T14:02:11Z"
        );

        TransactionResponse response = response(existing);

        when(repository.findById("evt-duplicate"))
                .thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing))
                .thenReturn(response);

        TransactionApplicationResult result =
                service.applyTransaction("acct-123", request);

        assertThat(result.created()).isFalse();
        assertThat(result.transaction()).isEqualTo(response);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCalculateBalanceAsCreditsMinusDebits() {
        List<AccountTransaction> transactions = List.of(
                transaction(
                        "evt-credit-1",
                        TransactionType.CREDIT,
                        "150.00",
                        "2026-05-15T15:00:00Z"
                ),
                transaction(
                        "evt-debit-1",
                        TransactionType.DEBIT,
                        "40.00",
                        "2026-05-15T13:00:00Z"
                ),
                transaction(
                        "evt-credit-2",
                        TransactionType.CREDIT,
                        "25.50",
                        "2026-05-15T14:00:00Z"
                )
        );

        when(repository.findByAccountId("acct-123"))
                .thenReturn(transactions);

        BalanceResponse response = service.getBalance("acct-123");

        assertThat(response.accountId()).isEqualTo("acct-123");
        assertThat(response.balance())
                .isEqualByComparingTo("135.50");
        assertThat(response.currency()).isEqualTo("USD");
    }

    @Test
    void shouldReturnRecentTransactionsInRepositoryOrder() {
        AccountTransaction newest = transaction(
                "evt-newest",
                TransactionType.CREDIT,
                "75.00",
                "2026-05-15T16:00:00Z"
        );

        AccountTransaction older = transaction(
                "evt-older",
                TransactionType.DEBIT,
                "10.00",
                "2026-05-15T14:00:00Z"
        );

        when(repository.findByAccountIdOrderByEventTimestampDesc("acct-123"))
                .thenReturn(List.of(newest, older));

        when(mapper.toResponse(newest))
                .thenReturn(response(newest));
        when(mapper.toResponse(older))
                .thenReturn(response(older));

        AccountDetailsResponse response =
                service.getAccountDetails("acct-123");

        assertThat(response.balance())
                .isEqualByComparingTo("65.00");

        assertThat(response.recentTransactions())
                .extracting(TransactionResponse::eventId)
                .containsExactly("evt-newest", "evt-older");
    }

    @Test
    void shouldRejectConflictingDuplicateTransaction() {
        ApplyTransactionRequest request = request(
                "evt-conflict",
                TransactionType.DEBIT,
                "75.00",
                "2026-05-15T15:00:00Z"
        );

        AccountTransaction existing = transaction(
                "evt-conflict",
                TransactionType.CREDIT,
                "150.00",
                "2026-05-15T14:02:11Z"
        );

        when(repository.findById("evt-conflict"))
                .thenReturn(Optional.of(existing));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.applyTransaction("acct-123", request)
        ).isInstanceOf(
                com.nthippar.eventledger.account.error
                        .ConflictingTransactionException.class
        );

        verify(repository, never()).saveAndFlush(any());
    }

    private ApplyTransactionRequest request(
            String eventId,
            TransactionType type,
            String amount,
            String timestamp
    ) {
        return new ApplyTransactionRequest(
                eventId,
                type,
                new BigDecimal(amount),
                "USD",
                Instant.parse(timestamp)
        );
    }

    private AccountTransaction transaction(
            String eventId,
            TransactionType type,
            String amount,
            String timestamp
    ) {
        return new AccountTransaction(
                eventId,
                "acct-123",
                type,
                new BigDecimal(amount),
                "USD",
                Instant.parse(timestamp)
        );
    }

    private TransactionResponse response(
            AccountTransaction transaction
    ) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp()
        );
    }
}