package sudar.chits.sheet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import sudar.chits.sheet.entity.ChitCollection;
import sudar.chits.sheet.entity.MonthlySheet;

public record PaymentResponse(
		Integer year,
		Integer month,
		Integer chitNo,
		Integer paidMonth,
		BigDecimal dueAmount,
		BigDecimal paidAmount,
		LocalDate paidDate,
		String remark
) {
	public static PaymentResponse from(MonthlySheet s, ChitCollection c) {
		return new PaymentResponse(s.getSheetYear(), s.getSheetMonth(), c.getChitNo(), c.getPaidMonth(),
				c.getDueAmount(), c.getPaidAmount(), c.getPaidDate(), c.getRemark());
	}
}
