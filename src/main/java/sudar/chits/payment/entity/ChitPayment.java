package sudar.chits.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One receipt: a member paid this amount on this date. The month sheet's வரவு is the sum of these. */
@Entity
@Table(name = "chit_payments")
public class ChitPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "chit_no", nullable = false)
	private Integer chitNo;

	@Column(name = "paid_date", nullable = false)
	private LocalDate paidDate;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "remark", length = 500)
	private String remark;

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
	public Integer getChitNo() { return chitNo; }
	public void setChitNo(Integer chitNo) { this.chitNo = chitNo; }
	public LocalDate getPaidDate() { return paidDate; }
	public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal amount) { this.amount = amount; }
	public String getRemark() { return remark; }
	public void setRemark(String remark) { this.remark = remark; }
	public Integer getCreatedBy() { return createdBy; }
	public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
	public Integer getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
