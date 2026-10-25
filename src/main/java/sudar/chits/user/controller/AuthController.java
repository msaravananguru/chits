package sudar.chits.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sudar.chits.user.dto.UserResponse;
import sudar.chits.user.service.CurrentUserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final CurrentUserService currentUserService;

	public AuthController(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	/** Who am I? Uses the X-User-Id header for now; the real login will be added here later. */
	@PostMapping("/me")
	public ResponseEntity<UserResponse> me() {
		return ResponseEntity.ok(UserResponse.from(currentUserService.requireUser()));
	}
}
