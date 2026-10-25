package sudar.chits.collection.dto;

import java.time.LocalDate;
import java.util.List;

import sudar.chits.sheet.dto.SheetListItem;

public record SingleListResponse(
		Integer year,
		Integer month,
		String source,                 // FINAL / LIVE / NONE
		String sheetStatus,            // DRAFT / LOCKED / NONE
		boolean editable,
		LocalDate today,
		int dailyCount,
		int singleCount,
		List<SheetListItem> months,
		SingleSummary summary,
		List<SingleRow> rows
) {
}
