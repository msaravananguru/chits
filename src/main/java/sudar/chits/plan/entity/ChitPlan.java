package sudar.chits.plan.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chit_plans")
public class ChitPlan {

	@Id
	@Column(name = "plan_amount", precision = 12, scale = 2)
	private BigDecimal planAmount;

	@Column(name = "total_months", nullable = false)
	private Integer totalMonths;

	@Column(name = "monthly_before_taken", nullable = false, precision = 12, scale = 2)
	private BigDecimal monthlyBeforeTaken;

	@Column(name = "monthly_after_taken", nullable = false, precision = 12, scale = 2)
	private BigDecimal monthlyAfterTaken;

	public BigDecimal getPlanAmount() { return planAmount; }
	public void setPlanAmount(BigDecimal planAmount) { this.planAmount = planAmount; }
	public Integer getTotalMonths() { return totalMonths; }
	public void setTotalMonths(Integer totalMonths) { this.totalMonths = totalMonths; }
	public BigDecimal getMonthlyBeforeTaken() { return monthlyBeforeTaken; }
	public void setMonthlyBeforeTaken(BigDecimal monthlyBeforeTaken) { this.monthlyBeforeTaken = monthlyBeforeTaken; }
	public BigDecimal getMonthlyAfterTaken() { return monthlyAfterTaken; }
	public void setMonthlyAfterTaken(BigDecimal monthlyAfterTaken) { this.monthlyAfterTaken = monthlyAfterTaken; }
}
