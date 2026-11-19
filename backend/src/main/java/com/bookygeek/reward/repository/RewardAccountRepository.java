package com.bookygeek.reward.repository;

import com.bookygeek.reward.entity.RewardAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RewardAccountRepository extends JpaRepository<RewardAccount, Long> {

    /** {@code SELECT ... FOR UPDATE}: serialises concurrent orders of one customer so the balance cannot go negative. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RewardAccount a where a.customerId = :customerId")
    Optional<RewardAccount> findForUpdate(@Param("customerId") long customerId);
}
