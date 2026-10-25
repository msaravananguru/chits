package sudar.chits.collection.dto;

import java.time.LocalDate;
import java.util.List;

/** Saves the whole day: every entry becomes that member's total for the date. remark is optional. */
public record SaveDayRequest(LocalDate date, String remark, List<SaveDayEntry> entries) {
}
