package sudar.chits.sheet.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monthly_sheet")
public class MonthlySheet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "sheet_year", nullable = false)
	private Integer sheetYear;

	@Column(name = "sheet_month", nullable = false)
	private Integer sheetMonth;

	@Column(name = "status", nullable = false, length = 10)
	private String status;          // DRAFT / LOCKED

	@Column(name = "locked_at")
	private LocalDateTime lockedAt;

	@Column(name = "created_by")
	private Integer createdBy;

	@Column(name = "locked_by")
	private Integer lockedBy;

	public Integer getId() { return id; }
	public void setId(Integer id) { this.id = id; }
	public Integer getSheetYear() { return sheetYear; }
	public void setSheetYear(Integer sheetYear) { this.sheetYear = sheetYear; }
	public Integer getSheetMonth() { return sheetMonth; }
	public void setSheetMonth(Integer sheetMonth) { this.sheetMonth = sheetMonth; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public LocalDateTime getLockedAt() { return lockedAt; }
	public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
	public Integer getCreatedBy() { return createdBy; }
	public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
	public Integer getLockedBy() { return lockedBy; }
	public void setLockedBy(Integer lockedBy) { this.lockedBy = lockedBy; }
}
