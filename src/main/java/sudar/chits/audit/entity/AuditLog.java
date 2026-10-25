package sudar.chits.audit.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// filled by the database (DEFAULT CURRENT_TIMESTAMP(3))
	@Column(name = "event_time", insertable = false, updatable = false)
	private LocalDateTime eventTime;

	@Column(name = "user_id")
	private Integer userId;

	@Column(name = "username", length = 100)
	private String username;

	@Column(name = "action", nullable = false, length = 30)
	private String action;

	@Column(name = "entity_name", length = 50)
	private String entityName;

	@Column(name = "entity_id", length = 50)
	private String entityId;

	// JSON text (MySQL JSON column)
	@Column(name = "old_values")
	private String oldValues;

	@Column(name = "new_values")
	private String newValues;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "http_method", length = 10)
	private String httpMethod;

	@Column(name = "endpoint", length = 200)
	private String endpoint;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "result", nullable = false, length = 10)
	private String result;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	public Long getId() { return id; }
	public LocalDateTime getEventTime() { return eventTime; }
	public Integer getUserId() { return userId; }
	public void setUserId(Integer userId) { this.userId = userId; }
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getAction() { return action; }
	public void setAction(String action) { this.action = action; }
	public String getEntityName() { return entityName; }
	public void setEntityName(String entityName) { this.entityName = entityName; }
	public String getEntityId() { return entityId; }
	public void setEntityId(String entityId) { this.entityId = entityId; }
	public String getOldValues() { return oldValues; }
	public void setOldValues(String oldValues) { this.oldValues = oldValues; }
	public String getNewValues() { return newValues; }
	public void setNewValues(String newValues) { this.newValues = newValues; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getHttpMethod() { return httpMethod; }
	public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
	public String getEndpoint() { return endpoint; }
	public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
	public String getIpAddress() { return ipAddress; }
	public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
	public String getResult() { return result; }
	public void setResult(String result) { this.result = result; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
