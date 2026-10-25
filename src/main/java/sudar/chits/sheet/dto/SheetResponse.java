package sudar.chits.sheet.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SheetResponse(
		Integer id,
		Integer year,
		Integer month,
		String status,               // DRAFT / LOCKED
		LocalDateTime lockedAt,
		List<SheetRowResponse> rows,
		SheetTotalsResponse totals
) {
}
