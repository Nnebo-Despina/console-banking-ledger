package com.team.banking.model;

import java.math.BigDecimal;

public interface InterestStrategy {

    BigDecimal calculateInterest(BigDecimal balance);

}
