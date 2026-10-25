package sudar.chits.payment.controller;

import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sudar.chits.audit.service.AuditService;
import sudar.chits.common.exception.ApiException;
import sudar.chits.payment.dto.AddPaymentRequest;
import sudar.chits.payment.dto.PaymentEntryResponse;
import sudar.chits.payment.dto.PaymentIdRequest;
import sudar.chits.payment.dto.PaymentListRequest;
import sudar.chits.payment.dto.PaymentListResponse;
import sudar.chits.payment.dto.UpdatePaymentRequest;
import sudar.chits.payment.service.PaymentService;
import sudar.chits.user.entity.User;
import sudar.chits.user.service.CurrentUserService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;
	private final CurrentUserService currentUserService;
	private final AuditService auditService;

	public PaymentController(PaymentService paymentService, CurrentUserService currentUserService,
			AuditService auditService) {
		this.paymentService = paymentService;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
	}

	@PostMapping("/add")
	public ResponseEntity<PaymentEntryResponse> add(@RequestBody AddPaymentRequest request) {
		PaymentEntryResponse response = audited("ADD_PAYMENT", "chit_payments",
				request.chitNo() + "/" + request.paidDate(), user -> paymentService.add(request, user));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/update")
	public ResponseEntity<PaymentEntryResponse> update(@RequestBody UpdatePaymentRequest request) {
		return ResponseEntity.ok(audited("UPDATE_PAYMENT_ENTRY", "chit_payments", String.valueOf(request.id()),
				user -> paymentService.update(request, user)));
	}

	@PostMapping("/delete")
	public ResponseEntity<Void> delete(@RequestBody PaymentIdRequest request) {
		audited("DELETE_PAYMENT", "chit_payments", String.valueOf(request.id()), user -> {
			paymentService.delete(request, user);
			return Boolean.TRUE;
		});
		return ResponseEntity.noContent().build();
	}

	// read: the caller must be a valid user, but reads are not audited
	@PostMapping("/list")
	public ResponseEntity<PaymentListResponse> list(@RequestBody PaymentListRequest request) {
		currentUserService.requireUser();
		return ResponseEntity.ok(paymentService.list(request));
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
