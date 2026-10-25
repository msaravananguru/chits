package sudar.chits.collection.controller;

import java.util.function.Function;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sudar.chits.audit.service.AuditService;
import sudar.chits.collection.dto.AllListResponse;
import sudar.chits.collection.dto.DailyGridResponse;
import sudar.chits.collection.dto.SaveDayRequest;
import sudar.chits.collection.dto.SaveDayResponse;
import sudar.chits.collection.dto.SingleListResponse;
import sudar.chits.collection.service.CollectionService;
import sudar.chits.common.exception.ApiException;
import sudar.chits.sheet.dto.SheetPeriodRequest;
import sudar.chits.user.entity.User;
import sudar.chits.user.service.CurrentUserService;

@RestController
@RequestMapping("/api/collection")
public class CollectionController {

	private final CollectionService collectionService;
	private final CurrentUserService currentUserService;
	private final AuditService auditService;

	public CollectionController(CollectionService collectionService, CurrentUserService currentUserService,
			AuditService auditService) {
		this.collectionService = collectionService;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
	}

	// reads: the caller must be a valid user, but reads are not audited. The body is optional (default = open month)
	@PostMapping("/daily")
	public ResponseEntity<DailyGridResponse> daily(@RequestBody(required = false) SheetPeriodRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(collectionService.daily(request));
	}

	@PostMapping("/single")
	public ResponseEntity<SingleListResponse> single(@RequestBody(required = false) SheetPeriodRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(collectionService.single(request));
	}

	@PostMapping("/all")
	public ResponseEntity<AllListResponse> all(@RequestBody(required = false) SheetPeriodRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(collectionService.all(request));
	}

	@PostMapping("/save-day")
	public ResponseEntity<SaveDayResponse> saveDay(@RequestBody SaveDayRequest request) {
		return ResponseEntity.ok(audited("SAVE_COLLECTION_DAY", "collection_days",
				String.valueOf(request == null ? null : request.date()), user -> collectionService.saveDay(request, user)));
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
