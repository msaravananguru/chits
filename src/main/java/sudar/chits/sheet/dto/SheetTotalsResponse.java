package sudar.chits.sheet.dto;

import java.math.BigDecimal;

// footer row: இந்த மாத வரவு
public record SheetTotalsResponse(
		int rows,
		BigDecimal dueAmount,
		BigDecimal paidAmount,
		BigDecimal totalPaid,
		BigDecimal totalDueAmount,
		BigDecimal remainingAmount
) {
}
