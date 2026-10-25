package sudar.chits.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import sudar.chits.common.exception.ApiException;
import sudar.chits.user.entity.User;
import sudar.chits.user.repository.UserRepository;
/**
 * Finds out who is calling the API.
 * For now the caller sends the header  X-User-Id.
 * When the real login is built, only this class changes.
 */
@Service
public class CurrentUserService {

	public static final String USER_HEADER = "X-User-Id";

	private final UserRepository userRepository;

	public CurrentUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** The calling user. Throws 401 / 403 if the header is missing, invalid or the user is inactive. */
	public User requireUser() {
		HttpServletRequest request = currentRequest();
		String header = request == null ? null : request.getHeader(USER_HEADER);
		if (header == null || header.isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Header " + USER_HEADER + " is required");
		}
		int id;
		try {
			id = Integer.parseInt(header.trim());
		} catch (NumberFormatException e) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Header " + USER_HEADER + " must be a number");
		}
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found: " + id));
		if (!"ACTIVE".equals(user.getStatus())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "User is inactive");
		}
		return user;
	}

	/** Same lookup but never throws; returns null if the user cannot be identified (used for failure audit). */
	public User findUserOrNull() {
		HttpServletRequest request = currentRequest();
		String header = request == null ? null : request.getHeader(USER_HEADER);
		if (header == null || header.isBlank()) {
			return null;
		}
		try {
			return userRepository.findById(Integer.parseInt(header.trim())).orElse(null);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public HttpServletRequest currentRequest() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
			return attrs.getRequest();
		}
		return null;
	}
}
