package sudar.chits.sheet.dto;

import java.time.LocalDateTime;

import sudar.chits.sheet.entity.MonthlySheet;

public record SheetListItem(Integer id, Integer year, Integer month, String status, LocalDateTime lockedAt) {

	public static SheetListItem from(MonthlySheet s) {
		return new SheetListItem(s.getId(), s.getSheetYear(), s.getSheetMonth(), s.getStatus(), s.getLockedAt());
	}
}
