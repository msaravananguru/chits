package sudar.chits.collection.dto;

import java.math.BigDecimal;

/** What one member paid on the day. 0 means "paid nothing" (no receipt is kept). */
public record SaveDayEntry(Integer chitNo, BigDecimal amount) {
}
