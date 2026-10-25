package sudar.chits.sheet.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chit_collections")
public class ChitCollection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "sheet_id", nullable = false)
	private Integer sheetId;

	@Column(name = "chit_no", nullable = false)
	private Integer chitNo;

	@Column(name = "paid_month", nullable = false)
	private Integer paidMonth;                 // current month number of the chit

	@Column(name = "due_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal dueAmount;              // கட்ட வேண்டிய தொகை

	@Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal paidAmount;             // வரவு

	@Column(name = "paid_date")
	private LocalDate paidDate;

	@Column(name = "remark", length = 500)
	private String remark;

	// snapshot values, filled when the month is LOCKED
	@Column(name = "total_paid", precision = 12, scale = 2)
	private BigDecimal totalPaid;

	@Column(name = "total_due_amount", precision = 12, scale = 2)
	private BigDecimal totalDueAmount;

	@Column(name = "months_pending", precision = 8, scale = 2)
	private BigDecimal monthsPending;

	@Column(name = "remaining_amount", precision = 12, scale = 2)
	private BigDecimal remainingAmount;

	@Column(name = "created_by")
	private Integer createdBy;

	@Column(name = "updated_by")
	private Integer updatedBy;

	// filled by the database
	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public Integer getId() { return id; }
	public void setId(Integer id) { this.id = id; }
	public Integer getSheetId() { return sheetId; }
	public void setSheetId(Integer sheetId) { this.sheetId = sheetId; }
	public Integer getChitNo() { return chitNo; }
	public void setChitNo(Integer chitNo) { this.chitNo = chitNo; }
	public Integer getPaidMonth() { return paidMonth; }
	public void setPaidMonth(Integer paidMonth) { this.paidMonth = paidMonth; }
	public BigDecimal getDueAmount() { return dueAmount; }
	public void setDueAmount(BigDecimal dueAmount) { this.dueAmount = dueAmount; }
	public BigDecimal getPaidAmount() { return paidAmount; }
	public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
	public LocalDate getPaidDate() { return paidDate; }
	public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
	public String getRemark() { return remark; }
	public void setRemark(String remark) { this.remark = remark; }
	public BigDecimal getTotalPaid() { return totalPaid; }
	public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
	public BigDecimal getTotalDueAmount() { return totalDueAmount; }
	public void setTotalDueAmount(BigDecimal totalDueAmount) { this.totalDueAmount = totalDueAmount; }
	public BigDecimal getMonthsPending() { return monthsPending; }
	public void setMonthsPending(BigDecimal monthsPending) { this.monthsPending = monthsPending; }
	public BigDecimal getRemainingAmount() { return remainingAmount; }
	public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }
	public Integer getCreatedBy() { return createdBy; }
	public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
	public Integer getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
