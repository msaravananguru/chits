package sudar.chits.sheet.controller;

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
import sudar.chits.sheet.dto.SheetListItem;
import sudar.chits.sheet.dto.SheetResponse;
import sudar.chits.sheet.dto.SheetPeriodRequest;
import sudar.chits.sheet.dto.SheetSummaryResponse;
import sudar.chits.sheet.service.SheetService;
import sudar.chits.user.entity.User;
import sudar.chits.user.service.CurrentUserService;

@RestController
@RequestMapping("/api/sheets")
public class SheetController {

	private final SheetService sheetService;
	private final CurrentUserService currentUserService;
	private final AuditService auditService;

	public SheetController(SheetService sheetService, CurrentUserService currentUserService,
			AuditService auditService) {
		this.sheetService = sheetService;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
	}

	@PostMapping("/generate")
	public ResponseEntity<SheetSummaryResponse> generate(@RequestBody SheetPeriodRequest request) {
		SheetSummaryResponse response = audited("GENERATE_MONTH", "monthly_sheet",
				request.year() + "-" + request.month(), user -> sheetService.generate(request, user));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/lock")
	public ResponseEntity<SheetResponse> lock(@RequestBody SheetPeriodRequest request) {
		return ResponseEntity.ok(audited("LOCK_MONTH", "monthly_sheet", request.year() + "-" + request.month(),
				user -> sheetService.lock(request, user)));
	}

	// reads: the caller must be a valid user, but reads are not audited
	@PostMapping("/get")
	public ResponseEntity<SheetResponse> get(@RequestBody SheetPeriodRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(sheetService.getSheet(request));
	}

	@PostMapping("/list")
	public ResponseEntity<List<SheetListItem>> list() {
		currentUserService.requireUser();
		return ResponseEntity.ok(sheetService.listSheets());
	}

	/** Runs a write action as the calling user; a failed attempt is saved in the audit log as FAILED. */
	private <T> T audited(String action, String entityName, String entityId, Function<User, T> work) {
		try {
			User user = currentUserService.requireUser();
			return work.apply(user);
		} catch (ApiException ex) {
			auditService.logFailure(action, entityName, entityId, ex.getMessage());
			throw ex;
		}
	}
}
