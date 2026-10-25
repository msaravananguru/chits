package sudar.chits.sheet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SheetRowResponse(
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		BigDecimal planAmount,
		Integer takenMonth,          // null = not taken
		Integer paidMonth,           // சீட்டு கட்டும் மாதம்: current month number of the chit
		BigDecimal dueAmount,        // கட்ட வேண்டிய தொகை
		BigDecimal paidAmount,       // வரவு (sum of this month's receipts)
		LocalDate paidDate,
		String remark,
		Integer paymentCount,        // how many receipts make up the வரவு
		BigDecimal totalPaid,        // மொத்தம் செலுத்திய தொகை
		BigDecimal totalDueAmount,   // மொத்தம் செலுத்த வேண்டிய தொகை
		BigDecimal monthsPending,    // Months Pending
		BigDecimal remainingAmount,  // BALANCE AMOUNT
		String payMode,              // SINGLE / DAILY (current setting of the member)
		BigDecimal dailyAmount       // usual daily amount, may be null
) {
}
