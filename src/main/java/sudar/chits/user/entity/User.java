package sudar.chits.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "username", nullable = false, length = 100)
	private String username;

	// plain text for now; will be hashed after the project is complete
	@Column(name = "password", nullable = false, length = 255)
	private String password;

	@Column(name = "full_name", length = 200)
	private String fullName;

	@Column(name = "role", nullable = false, length = 10)
	private String role;

	@Column(name = "status", nullable = false, length = 10)
	private String status;

	public Integer getId() { return id; }
	public void setId(Integer id) { this.id = id; }
	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }
	public String getFullName() { return fullName; }
	public void setFullName(String fullName) { this.fullName = fullName; }
	public String getRole() { return role; }
	public void setRole(String role) { this.role = role; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
}
