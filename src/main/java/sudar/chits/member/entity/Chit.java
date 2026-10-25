package sudar.chits.member.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chits")
public class Chit {

	@Id
	@Column(name = "chit_no")
	private Integer chitNo;

	@Column(name = "member_name", nullable = false, length = 200)
	private String memberName;

	@Column(name = "member_name_tamil", length = 200)
	private String memberNameTamil;

	@Column(name = "plan_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal planAmount;

	@Column(name = "joined_date", nullable = false)
	private LocalDate joinedDate;

	@Column(name = "taken_month")
	private Integer takenMonth;

	@Column(name = "status", nullable = false, length = 10)
	private String status;

	// amount paid before the system started
	@Column(name = "opening_paid", nullable = false, precision = 12, scale = 2)
	private BigDecimal openingPaid = BigDecimal.ZERO;

	// SINGLE = pays the full amount at once, DAILY = pays part by part (daily collection)
	@Column(name = "pay_mode", nullable = false, length = 10)
	private String payMode = "SINGLE";

	// optional usual daily amount (only a hint for the collector)
	@Column(name = "daily_amount", precision = 12, scale = 2)
	private BigDecimal dailyAmount;

	@Column(name = "created_by")
	private Integer createdBy;

	@Column(name = "updated_by")
	private Integer updatedBy;

	// filled by the database (ON UPDATE CURRENT_TIMESTAMP)
	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public Integer getChitNo() { return chitNo; }
	public void setChitNo(Integer chitNo) { this.chitNo = chitNo; }
	public String getMemberName() { return memberName; }
	public void setMemberName(String memberName) { this.memberName = memberName; }
	public String getMemberNameTamil() { return memberNameTamil; }
	public void setMemberNameTamil(String memberNameTamil) { this.memberNameTamil = memberNameTamil; }
	public BigDecimal getPlanAmount() { return planAmount; }
	public void setPlanAmount(BigDecimal planAmount) { this.planAmount = planAmount; }
	public LocalDate getJoinedDate() { return joinedDate; }
	public void setJoinedDate(LocalDate joinedDate) { this.joinedDate = joinedDate; }
	public Integer getTakenMonth() { return takenMonth; }
	public void setTakenMonth(Integer takenMonth) { this.takenMonth = takenMonth; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public BigDecimal getOpeningPaid() { return openingPaid; }
	public void setOpeningPaid(BigDecimal openingPaid) { this.openingPaid = openingPaid; }
	public String getPayMode() { return payMode; }
	public void setPayMode(String payMode) { this.payMode = payMode; }
	public BigDecimal getDailyAmount() { return dailyAmount; }
	public void setDailyAmount(BigDecimal dailyAmount) { this.dailyAmount = dailyAmount; }
	public Integer getCreatedBy() { return createdBy; }
	public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
	public Integer getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
