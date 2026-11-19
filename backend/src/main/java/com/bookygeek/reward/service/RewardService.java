package com.bookygeek.reward.service;

import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import com.bookygeek.reward.entity.RewardAccount;
import com.bookygeek.reward.entity.RewardTransaction;
import com.bookygeek.reward.repository.RewardAccountRepository;
import com.bookygeek.reward.repository.RewardTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Reward points. Customers never earn points: a new account starts with {@code bookygeek.rewards.initial-points} and
 * points are only redeemed at order placement and restored at cancellation. Every method joins the caller's
 * transaction, so an order and its points movement commit or roll back together. The Order module uses this
 * service only; it never touches the reward repositories or entities.
 */
@Service
public class RewardService {

    private final RewardAccountRepository accounts;
    private final RewardTransactionRepository transactions;
    private final BookygeekProperties properties;
    private final Clock clock;

    public RewardService(RewardAccountRepository accounts, RewardTransactionRepository transactions,
            BookygeekProperties properties, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates the account of a newly registered customer with the configured starting balance (no ledger row). */
    @Transactional
    public void createAccount(long customerId) {
        accounts.save(new RewardAccount(customerId, properties.rewards().initialPoints()));
    }

    /** Current balance of the customer's account. */
    @Transactional(readOnly = true)
    public int getBalance(long customerId) {
        return accounts.findById(customerId).map(RewardAccount::getBalance)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * Locks the customer's account row ({@code FOR UPDATE}) until the surrounding transaction ends and returns the
     * balance read under the lock. Order placement calls this before validating the points to redeem.
     */
    @Transactional
    public int lockAccount(long customerId) {
        return lock(customerId).getBalance();
    }

    /**
     * Redeems {@code points} for an order. Zero points is a no-op and writes no ledger row. More points than the
     * balance is rejected with {@code POINTS_LIMIT_EXCEEDED}; the balance never becomes negative.
     */
    @Transactional
    public void deduct(long customerId, long orderId, int points) {
        if (points < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
        if (points == 0) {
            return;
        }
        RewardAccount account = lock(customerId);
        if (points > account.getBalance()) {
            throw new ApiException(ErrorCode.POINTS_LIMIT_EXCEEDED);
        }
        account.setBalance(account.getBalance() - points);
        transactions.save(new RewardTransaction(customerId, orderId, RewardTransaction.Type.REDEEM, points,
                clock.instant()));
    }

    /**
     * Gives back the points redeemed on a cancelled order. Zero points writes nothing, and a second restore for the
     * same order is ignored (the ledger allows one {@code RESTORE} per order).
     */
    @Transactional
    public void restore(long customerId, long orderId, int points) {
        if (points < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
        if (points == 0 || transactions.existsByOrderIdAndType(orderId, RewardTransaction.Type.RESTORE)) {
            return;
        }
        RewardAccount account = lock(customerId);
        account.setBalance(account.getBalance() + points);
        transactions.save(new RewardTransaction(customerId, orderId, RewardTransaction.Type.RESTORE, points,
                clock.instant()));
    }

    private RewardAccount lock(long customerId) {
        return accounts.findForUpdate(customerId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
