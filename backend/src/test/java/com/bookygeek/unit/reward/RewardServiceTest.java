package com.bookygeek.unit.reward;

import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import com.bookygeek.reward.entity.RewardAccount;
import com.bookygeek.reward.entity.RewardTransaction;
import com.bookygeek.reward.repository.RewardAccountRepository;
import com.bookygeek.reward.repository.RewardTransactionRepository;
import com.bookygeek.reward.service.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Account creation and ledger rules (T048); no Spring context and no database. */
class RewardServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private RewardAccountRepository accounts;
    private RewardTransactionRepository transactions;
    private RewardService service;

    private static BookygeekProperties properties(int initialPoints) {
        return new BookygeekProperties(new BookygeekProperties.Rewards(initialPoints, 50),
                new BookygeekProperties.Tax(new BigDecimal("0.05")),
                new BookygeekProperties.Delivery(new BigDecimal("40.00")),
                new BookygeekProperties.Cancellation(48),
                List.of(new BookygeekProperties.PriceBand("All", null, null)));
    }

    private RewardService serviceWith(int initialPoints) {
        return new RewardService(accounts, transactions, properties(initialPoints), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        accounts = mock(RewardAccountRepository.class);
        transactions = mock(RewardTransactionRepository.class);
        service = serviceWith(1000);
    }

    private void accountWithBalance(long customerId, int balance) {
        when(accounts.findForUpdate(customerId)).thenReturn(Optional.of(new RewardAccount(customerId, balance)));
    }

    // ---- account creation -----------------------------------------------------------------------------------------

    @Test
    void newAccountStartsWithTheConfiguredInitialPointsAndWritesNoLedgerRow() {
        service.createAccount(5L);

        ArgumentCaptor<RewardAccount> saved = ArgumentCaptor.forClass(RewardAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo(5L);
        assertThat(saved.getValue().getBalance()).isEqualTo(1000);
        verify(transactions, never()).save(any());
    }

    @Test
    void initialPointsComeFromConfigurationNotFromACode() {
        serviceWith(250).createAccount(5L);

        ArgumentCaptor<RewardAccount> saved = ArgumentCaptor.forClass(RewardAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getBalance()).isEqualTo(250);
    }

    @Test
    void balanceOfAMissingAccountIsNotFound() {
        when(accounts.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBalance(9L))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void balanceIsReadFromTheAccount() {
        when(accounts.findById(5L)).thenReturn(Optional.of(new RewardAccount(5L, 730)));
        assertThat(service.getBalance(5L)).isEqualTo(730);
    }

    // ---- redeem ---------------------------------------------------------------------------------------------------

    @Test
    void lockReadsTheBalanceThroughTheForUpdateLookup() {
        accountWithBalance(5L, 800);
        assertThat(service.lockAccount(5L)).isEqualTo(800);
        verify(accounts).findForUpdate(5L);
    }

    @Test
    void deductReducesTheBalanceAndWritesOneRedeemRow() {
        accountWithBalance(5L, 1000);
        service.deduct(5L, 77L, 300);

        ArgumentCaptor<RewardTransaction> row = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(transactions).save(row.capture());
        assertThat(row.getValue().getType()).isEqualTo(RewardTransaction.Type.REDEEM);
        assertThat(row.getValue().getPoints()).isEqualTo(300);
        assertThat(row.getValue().getOrderId()).isEqualTo(77L);
        assertThat(row.getValue().getCustomerId()).isEqualTo(5L);
        assertThat(row.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(accounts.findForUpdate(5L).orElseThrow().getBalance()).isEqualTo(700);
    }

    @Test
    void redeemingTheWholeBalanceIsAllowedAndLeavesZero() {
        RewardAccount account = new RewardAccount(5L, 120);
        when(accounts.findForUpdate(5L)).thenReturn(Optional.of(account));
        service.deduct(5L, 1L, 120);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    void redeemingMoreThanTheBalanceIsRejectedAndChangesNothing() {
        RewardAccount account = new RewardAccount(5L, 100);
        when(accounts.findForUpdate(5L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.deduct(5L, 1L, 101))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.POINTS_LIMIT_EXCEEDED));
        assertThat(account.getBalance()).isEqualTo(100);
        verify(transactions, never()).save(any());
    }

    @Test
    void redeemingZeroPointsWritesNoLedgerRowAndDoesNotEvenLock() {
        service.deduct(5L, 1L, 0);
        verify(transactions, never()).save(any());
        verify(accounts, never()).findForUpdate(anyLong());
    }

    @Test
    void negativePointsAreAProgrammingErrorForBothMovements() {
        assertThatThrownBy(() -> service.deduct(5L, 1L, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.restore(5L, 1L, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- restore --------------------------------------------------------------------------------------------------

    @Test
    void restoreGivesPointsBackAndWritesOneRestoreRow() {
        RewardAccount account = new RewardAccount(5L, 700);
        when(accounts.findForUpdate(5L)).thenReturn(Optional.of(account));
        when(transactions.existsByOrderIdAndType(77L, RewardTransaction.Type.RESTORE)).thenReturn(false);

        service.restore(5L, 77L, 300);

        ArgumentCaptor<RewardTransaction> row = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(transactions).save(row.capture());
        assertThat(row.getValue().getType()).isEqualTo(RewardTransaction.Type.RESTORE);
        assertThat(row.getValue().getPoints()).isEqualTo(300);
        assertThat(account.getBalance()).isEqualTo(1000);
    }

    @Test
    void restoreOfZeroPointsWritesNothing() {
        service.restore(5L, 77L, 0);
        verify(transactions, never()).save(any());
        verify(accounts, never()).findForUpdate(anyLong());
    }

    @Test
    void aSecondRestoreForTheSameOrderIsIgnoredSoPointsAreNeverGivenBackTwice() {
        RewardAccount account = new RewardAccount(5L, 700);
        when(accounts.findForUpdate(5L)).thenReturn(Optional.of(account));
        when(transactions.existsByOrderIdAndType(77L, RewardTransaction.Type.RESTORE)).thenReturn(true);

        service.restore(5L, 77L, 300);

        assertThat(account.getBalance()).isEqualTo(700);
        verify(transactions, never()).save(any());
    }

    @Test
    void ledgerHasOnlyRedeemAndRestoreTypes() {
        assertThat(RewardTransaction.Type.values())
                .containsExactly(RewardTransaction.Type.REDEEM, RewardTransaction.Type.RESTORE);
    }
}
