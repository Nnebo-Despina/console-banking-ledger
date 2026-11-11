
package com.team.banking.model;

import java.math.BigDecimal;

public interface FeeStrategy {

    BigDecimal calculateFee(BigDecimal withdrawalAmount);

}
