package sudar.chits.collection.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AllRow(
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		Integer takenMonth,
		String payMode,                // DAILY / SINGLE
		BigDecimal dailyAmount,        // usual daily amount (Daily members; may be null)
		BigDecimal dueAmount,
		BigDecimal paidAmount,
		BigDecimal balance,            // due - paid this month (never negative)
		BigDecimal previousBalance,    // paid before this month minus due before this month (+ advance, - arrears)
		BigDecimal toPay,              // due - previousBalance - paid this month (never negative)
		LocalDate lastPaidDate,
		Integer paymentCount,
		String status                  // PAID / PART / NOT_PAID / NONE, judged on toPay
) {
}
