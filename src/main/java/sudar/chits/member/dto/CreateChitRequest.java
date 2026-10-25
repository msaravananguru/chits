package sudar.chits.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateChitRequest(
		Integer chitNo,
		String memberName,
		String memberNameTamil,   // optional
		BigDecimal planAmount,
		LocalDate joinedDate,
		Integer takenMonth,       // optional, null = not taken
		BigDecimal openingPaid    // optional, amount paid before the system started (default 0)
) {
}
