package com.bookygeek.reward.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A customer's reward balance (one point is worth one rupee). The balance can never be negative. */
@Entity
@Table(name = "reward_account")
public class RewardAccount {

    @Id
    @Column(name = "customer_id")
    private long customerId;

    @Column(name = "balance", nullable = false)
    private int balance;

    protected RewardAccount() {
        // for JPA
    }

    public RewardAccount(long customerId, int balance) {
        this.customerId = customerId;
        this.balance = balance;
    }

    public long getCustomerId() {
        return customerId;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }
}
