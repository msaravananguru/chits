package sudar.chits.member.dto;

import java.util.List;

import sudar.chits.sheet.dto.SheetListItem;

public record MemberOverviewResponse(
		Integer year,
		Integer month,
		String source,               // FINAL = read from the locked master table, LIVE = calculated now
		String sheetStatus,          // LOCKED / DRAFT / NONE (no sheet generated for this month yet)
		List<SheetListItem> months,  // months that have a sheet, newest first (for the month picker)
		MemberOverviewSummary summary,
		List<MemberOverviewRow> rows
) {
}
