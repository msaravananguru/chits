package sudar.chits.plan.dto;

import java.math.BigDecimal;

import sudar.chits.plan.entity.ChitPlan;

public record PlanResponse(
		BigDecimal planAmount,
		Integer totalMonths,
		BigDecimal monthlyBeforeTaken,
		BigDecimal monthlyAfterTaken
) {
	public static PlanResponse from(ChitPlan p) {
		return new PlanResponse(p.getPlanAmount(), p.getTotalMonths(),
				p.getMonthlyBeforeTaken(), p.getMonthlyAfterTaken());
	}
}
