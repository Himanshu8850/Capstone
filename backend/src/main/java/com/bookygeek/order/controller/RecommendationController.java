package com.bookygeek.order.controller;

import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.common.security.AuthenticatedCustomer;
import com.bookygeek.order.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code getBasketRecommendations} exactly as in {@code api/openapi.yaml}; signed-in customers only. */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping
    public List<BookSummary> getBasketRecommendations(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return recommendations.recommendationsFor(customer.id());
    }
}
