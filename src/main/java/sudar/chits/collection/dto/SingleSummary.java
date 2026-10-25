package sudar.chits.collection.dto;

import java.math.BigDecimal;

public record SingleSummary(int count, int paidCount, BigDecimal collected, BigDecimal toCollect) {
}
