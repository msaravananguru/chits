package sudar.chits.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A field left out (null) stays unchanged. An empty remark ("") clears it. */
public record UpdatePaymentRequest(Integer id, LocalDate paidDate, BigDecimal amount, String remark) {
}
