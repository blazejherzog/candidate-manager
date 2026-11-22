package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.ContractType;
import com.candidate_manager.job.domain.Salary;
import com.candidate_manager.job.domain.SalaryCurrency;
import com.candidate_manager.job.domain.SalaryPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SalaryRequest(
        @NotNull ContractType contractType,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal minAmount,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal maxAmount,
        @NotNull SalaryCurrency currency,
        @NotNull SalaryPeriod period) {

    @AssertTrue(message = "maxAmount must not be lower than minAmount")
    boolean isMaxAmountNotLowerThanMinAmount() {
        return minAmount == null || maxAmount == null || maxAmount.compareTo(minAmount) >= 0;
    }

    Salary toSalary() {
        return new Salary(contractType, minAmount, maxAmount, currency, period);
    }
}
