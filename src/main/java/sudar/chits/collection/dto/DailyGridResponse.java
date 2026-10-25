package sudar.chits.collection.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import sudar.chits.sheet.dto.SheetListItem;

public record DailyGridResponse(
		Integer year,
		Integer month,
		String source,                 // FINAL (locked month) / LIVE (open month) / NONE (no sheet yet)
		String sheetStatus,            // DRAFT / LOCKED / NONE
		boolean editable,              // true only for the open (DRAFT) month
		LocalDate today,               // server date: days after it cannot be entered
		int daysInMonth,
		int dailyCount,                // members paying daily
		int singleCount,               // members paying single
		List<SheetListItem> months,
		List<Integer> savedDays,       // days of the month that were saved on the Daily page
		List<DailyGridRow> rows,
		List<BigDecimal> dayTotals,    // "Total day Collection" for each day
		BigDecimal monthTotal
) {
}
