package sudar.chits.sheet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SheetPaymentRequest(
		Integer year,
		Integer month,
		Integer chitNo,
		BigDecimal paidAmount,   // வரவு received this month (0 = nothing received)
		LocalDate paidDate,      // required when paidAmount > 0
		String remark            // optional note (max 500 characters)
) {
}
