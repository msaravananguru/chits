package sudar.chits.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// null field = no change. memberNameTamil "" (blank) = clear it.
public record UpdateChitRequest(
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		LocalDate joinedDate,
		String status,            // ACTIVE / CLOSED
		BigDecimal openingPaid    // amount paid before the system started
) {
}
