package sudar.chits.member.dto;

import java.math.BigDecimal;

/** Numbers for the stat cards (ACTIVE chits only). */
public record MemberOverviewSummary(
		int activeCount,
		int takenCount,
		int paidCount,               // chits fully paid for the selected month
		BigDecimal totalPaid
) {
}
