package com.bookygeek.reward.repository;

import com.bookygeek.reward.entity.RewardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    boolean existsByOrderIdAndType(long orderId, RewardTransaction.Type type);
}
