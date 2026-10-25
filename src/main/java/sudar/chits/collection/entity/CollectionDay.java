package sudar.chits.collection.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Marks a day whose Daily collection was saved, so a member who paid 0 shows 0 and not "not entered yet". */
@Entity
@Table(name = "collection_days")
public class CollectionDay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "day_date", nullable = false)
	private LocalDate dayDate;

	@Column(name = "save_count", nullable = false)
	private Integer saveCount = 1;

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
	public LocalDate getDayDate() { return dayDate; }
	public void setDayDate(LocalDate dayDate) { this.dayDate = dayDate; }
	public Integer getSaveCount() { return saveCount; }
	public void setSaveCount(Integer saveCount) { this.saveCount = saveCount; }
	public Integer getCreatedBy() { return createdBy; }
	public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
	public Integer getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
