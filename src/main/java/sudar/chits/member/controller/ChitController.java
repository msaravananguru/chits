package sudar.chits.member.controller;

import java.util.List;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sudar.chits.audit.service.AuditService;
import sudar.chits.common.exception.ApiException;
import sudar.chits.member.dto.ChitListRequest;
import sudar.chits.member.dto.ChitNoRequest;
import sudar.chits.member.dto.ChitResponse;
import sudar.chits.member.dto.CreateChitRequest;
import sudar.chits.member.dto.MarkTakenRequest;
import sudar.chits.member.dto.UpdateChitRequest;
import sudar.chits.member.service.ChitService;
import sudar.chits.user.entity.User;
import sudar.chits.user.service.CurrentUserService;

@RestController
@RequestMapping("/api/chits")
public class ChitController {

	private final ChitService chitService;
	private final CurrentUserService currentUserService;
	private final AuditService auditService;

	public ChitController(ChitService chitService, CurrentUserService currentUserService,
			AuditService auditService) {
		this.chitService = chitService;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
	}

	@PostMapping("/create")
	public ResponseEntity<ChitResponse> create(@RequestBody CreateChitRequest request) {
		ChitResponse response = audited("CREATE_CHIT", request.chitNo(),
				user -> chitService.createChit(request, user));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/update")
	public ResponseEntity<ChitResponse> update(@RequestBody UpdateChitRequest request) {
		return ResponseEntity.ok(audited("UPDATE_CHIT", request.chitNo(),
				user -> chitService.updateChit(request, user)));
	}

	@PostMapping("/taken")
	public ResponseEntity<ChitResponse> markTaken(@RequestBody MarkTakenRequest request) {
		return ResponseEntity.ok(audited("MARK_TAKEN", request.chitNo(),
				user -> chitService.markTaken(request, user)));
	}

	// reads: the caller must be a valid user, but reads are not audited
	@PostMapping("/get")
	public ResponseEntity<ChitResponse> get(@RequestBody ChitNoRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(chitService.getChit(request.chitNo()));
	}

	@PostMapping("/list")
	public ResponseEntity<List<ChitResponse>> list(@RequestBody(required = false) ChitListRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(chitService.listChits(request == null ? null : request.status()));
	}

	/** Runs a write action as the calling user; a failed attempt is saved in the audit log as FAILED. */
	private <T> T audited(String action, Integer chitNo, Function<User, T> work) {
		try {
			User user = currentUserService.requireUser();
			return work.apply(user);
		} catch (ApiException ex) {
			auditService.logFailure(action, "chits", chitNo == null ? null : String.valueOf(chitNo),
					ex.getMessage());
			throw ex;
		}
	}
}
