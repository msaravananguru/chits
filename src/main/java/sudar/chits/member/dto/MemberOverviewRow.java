package sudar.chits.member.dto;

import java.math.BigDecimal;

/** One line of the Members page for the selected month. */
public record MemberOverviewRow(
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		BigDecimal planAmount,
		String status,               // ACTIVE / CLOSED (current)
		Integer takenMonth,          // null = not taken (as it was in the selected month)
		Integer chitMonth,           // which month of the chit it is running in the selected month (1 = joined month)
		Integer totalMonths,         // months in the plan (21)
		boolean completed,           // chitMonth is beyond totalMonths
		BigDecimal dueAmount,        // amount due for the selected month
		BigDecimal paidAmount,       // received in the selected month
		String monthStatus,          // PAID / PART / NOT_PAID / NONE (nothing due)
		BigDecimal totalPaid,
		BigDecimal totalToPay,
		BigDecimal remaining,
		String payMode,              // SINGLE / DAILY
		BigDecimal dailyAmount,      // usual daily amount, may be null
		BigDecimal previousBalance,  // paid before the month minus due before the month (+ advance, - arrears)
		BigDecimal toPay             // due - previousBalance - paid in the month (never negative)
) {
}
