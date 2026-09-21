package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.rule.CreateReplenishmentRuleRequest;
import io.tenoro.app.api.dto.rule.ReplenishmentRuleResponse;
import io.tenoro.app.domain.model.ReplenishmentRule;

public class ReplenishmentRuleResponseMapper {

    public static ReplenishmentRuleResponse fromDomain(ReplenishmentRule rule) {
        return ReplenishmentRuleResponse.builder()
                .sku(rule.getSku())
                .locationCode(rule.getLocationCode())
                .min(rule.getMin())
                .max(rule.getMax())
                .build();
    }

    /**
     * 0 <= min <= max validation happens here, at the DTO/mapping boundary, via ReplenishmentRule's own
     * validating constructor (SPECS.md endpoint #5 rule 2, BR-03): an invalid range throws
     * IllegalArgumentException, which the scoped ReplenishmentExceptionHandler maps to 400.
     */
    public static ReplenishmentRule toDomain(CreateReplenishmentRuleRequest request) {
        return ReplenishmentRule.builder()
                .sku(request.getSku())
                .locationCode(request.getLocationCode())
                .min(request.getMin())
                .max(request.getMax())
                .build();
    }
}
