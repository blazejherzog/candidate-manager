package com.candidate_manager.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public record Salary(
        @Enumerated(EnumType.STRING) @Column(name = "contract_type", nullable = false) ContractType contractType,
        @Column(name = "min_amount", nullable = false, precision = 12, scale = 2) BigDecimal minAmount,
        @Column(name = "max_amount", nullable = false, precision = 12, scale = 2) BigDecimal maxAmount,
        @Enumerated(EnumType.STRING) @Column(name = "currency", nullable = false) SalaryCurrency currency,
        @Enumerated(EnumType.STRING) @Column(name = "period", nullable = false) SalaryPeriod period) {
}
