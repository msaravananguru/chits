package sudar.chits.audit.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

import sudar.chits.audit.entity.AuditLog;
import sudar.chits.audit.repository.AuditLogRepository;
import sudar.chits.user.entity.User;
import sudar.chits.user.service.CurrentUserService;
@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;
	private final CurrentUserService currentUserService;

	public AuditService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService) {
		this.auditLogRepository = auditLogRepository;
		this.currentUserService = currentUserService;
	}

	/** Runs inside the caller's transaction: if the change is rolled back, this row is rolled back too. */
	@Transactional
	public void logSuccess(User user, String action, String entityName, String entityId,
			Map<String, Object> oldValues, Map<String, Object> newValues, String description) {
		save(user, action, entityName, entityId, oldValues, newValues, description, "SUCCESS", null);
	}

	/** Runs in its own transaction so the FAILED row is kept even though the main change was rolled back. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void logFailure(String action, String entityName, String entityId, String errorMessage) {
		save(currentUserService.findUserOrNull(), action, entityName, entityId, null, null, null, "FAILED",
				errorMessage);
	}

	private void save(User user, String action, String entityName, String entityId,
			Map<String, Object> oldValues, Map<String, Object> newValues, String description,
			String result, String errorMessage) {

		AuditLog log = new AuditLog();
		if (user != null) {
			log.setUserId(user.getId());
			log.setUsername(user.getUsername());
		}
		log.setAction(action);
		log.setEntityName(entityName);
		log.setEntityId(entityId);
		log.setOldValues(toJson(oldValues));
		log.setNewValues(toJson(newValues));
		log.setDescription(cut(description, 500));
		log.setResult(result);
		log.setErrorMessage(cut(errorMessage, 500));

		HttpServletRequest request = currentUserService.currentRequest();
		if (request != null) {
			log.setHttpMethod(request.getMethod());
			log.setEndpoint(cut(request.getRequestURI(), 200));
			log.setIpAddress(request.getRemoteAddr());
		}

		auditLogRepository.save(log);
	}

	private String cut(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}

	// small JSON writer, so no extra library is needed
	private String toJson(Map<String, Object> map) {
		if (map == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> e : map.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(escape(e.getKey())).append("\":");
			Object v = e.getValue();
			if (v == null) {
				sb.append("null");
			} else if (v instanceof BigDecimal bd) {
				sb.append(bd.toPlainString());
			} else if (v instanceof Number || v instanceof Boolean) {
				sb.append(v);
			} else {
				sb.append('"').append(escape(v.toString())).append('"');
			}
		}
		return sb.append('}').toString();
	}

	private String escape(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			switch (c) {
			case '"' -> sb.append("\\\"");
			case '\\' -> sb.append("\\\\");
			case '\n' -> sb.append("\\n");
			case '\r' -> sb.append("\\r");
			case '\t' -> sb.append("\\t");
			default -> {
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
			}
		}
		return sb.toString();
	}
}
