package sudar.chits.payment.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaymentListResponse(Integer year, Integer month, String status, int count, BigDecimal total,
		List<PaymentEntryResponse> payments) {
}
