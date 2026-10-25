package sudar.chits.user.dto;

import sudar.chits.user.entity.User;

// never contains the password
public record UserResponse(Integer id, String username, String fullName, String role) {

	public static UserResponse from(User u) {
		return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getRole());
	}
}
