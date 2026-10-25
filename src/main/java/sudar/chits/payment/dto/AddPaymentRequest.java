package sudar.chits.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddPaymentRequest(Integer chitNo, LocalDate paidDate, BigDecimal amount, String remark) {
}
