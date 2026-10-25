package sudar.chits.collection.dto;

import java.math.BigDecimal;

public record AllSummary(int count, int paidCount, BigDecimal collected, BigDecimal toCollect,
		BigDecimal dailyCollected, BigDecimal singleCollected, BigDecimal dueTotal, BigDecimal arrears,
		BigDecimal advance) {
}
