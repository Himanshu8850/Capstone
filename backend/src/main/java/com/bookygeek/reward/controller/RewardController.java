package com.bookygeek.reward.controller;

import com.bookygeek.common.security.AuthenticatedCustomer;
import com.bookygeek.reward.dto.RewardAccountResponse;
import com.bookygeek.reward.service.RewardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code getRewardAccount} exactly as in {@code api/openapi.yaml}: the signed-in customer's own balance. The account is
 * looked up by the authenticated customer's id only, so no other customer's account can be requested.
 */
@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewards;

    public RewardController(RewardService rewards) {
        this.rewards = rewards;
    }

    @GetMapping("/me")
    public RewardAccountResponse getRewardAccount(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return new RewardAccountResponse(rewards.getBalance(customer.id()));
    }
}
