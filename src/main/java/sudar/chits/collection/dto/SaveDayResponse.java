package sudar.chits.collection.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaveDayResponse(
		LocalDate date,
		int added,        // new receipts
		int updated,      // receipts whose amount changed
		int removed,      // receipts removed because the amount was set to 0
		int unchanged,    // entries that already had this value
		BigDecimal dayTotal   // total of the saved members on that day
) {
}
