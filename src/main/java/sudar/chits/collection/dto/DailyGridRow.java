package sudar.chits.collection.dto;

import java.math.BigDecimal;
import java.util.List;

public record DailyGridRow(
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		Integer takenMonth,
		BigDecimal dailyAmount,        // usual daily amount (hint), may be null
		BigDecimal dueAmount,          // due this month
		BigDecimal paidAmount,         // total received this month
		BigDecimal balance,            // due - paid (negative = paid extra)
		BigDecimal previousBalance,    // paid before this month minus due before this month (+ advance, - arrears)
		BigDecimal toPay,              // due - previousBalance - paid this month (never negative)
		List<BigDecimal> days,         // total received on each day of the month (0 when nothing)
		List<Integer> counts           // number of receipts on each day (more than 1 = edit in Payments)
) {
}
