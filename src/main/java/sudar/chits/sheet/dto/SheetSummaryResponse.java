package sudar.chits.sheet.dto;

import sudar.chits.sheet.entity.MonthlySheet;

public record SheetSummaryResponse(
		Integer id,
		Integer year,
		Integer month,
		String status,
		int rowsCreated,
		int skipped      // active chits left out (not joined yet, or all months completed)
) {
	public static SheetSummaryResponse from(MonthlySheet s, int rowsCreated, int skipped) {
		return new SheetSummaryResponse(s.getId(), s.getSheetYear(), s.getSheetMonth(), s.getStatus(),
				rowsCreated, skipped);
	}
}
