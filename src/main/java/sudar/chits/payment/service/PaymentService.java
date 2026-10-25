package sudar.chits.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.audit.service.AuditService;
import sudar.chits.common.exception.ApiException;
import sudar.chits.member.entity.Chit;
import sudar.chits.member.repository.ChitRepository;
import sudar.chits.payment.dto.AddPaymentRequest;
import sudar.chits.payment.dto.PaymentEntryResponse;
import sudar.chits.payment.dto.PaymentIdRequest;
import sudar.chits.payment.dto.PaymentListRequest;
import sudar.chits.payment.dto.PaymentListResponse;
import sudar.chits.payment.dto.UpdatePaymentRequest;
import sudar.chits.payment.entity.ChitPayment;
import sudar.chits.payment.repository.ChitPaymentRepository;
import sudar.chits.sheet.entity.MonthlySheet;
import sudar.chits.sheet.repository.ChitCollectionRepository;
import sudar.chits.sheet.repository.MonthlySheetRepository;
import sudar.chits.user.entity.User;

/**
 * Receipts (the collection table). A receipt belongs to the month of its date.
 * A receipt dated in a LOCKED month can never be added, changed or deleted.
 */
@Service
public class PaymentService {

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

	private final ChitPaymentRepository paymentRepository;
	private final ChitRepository chitRepository;
	private final MonthlySheetRepository sheetRepository;
	private final ChitCollectionRepository collectionRepository;
	private final AuditService auditService;

	public PaymentService(ChitPaymentRepository paymentRepository, ChitRepository chitRepository,
			MonthlySheetRepository sheetRepository, ChitCollectionRepository collectionRepository,
			AuditService auditService) {
		this.paymentRepository = paymentRepository;
		this.chitRepository = chitRepository;
		this.sheetRepository = sheetRepository;
		this.collectionRepository = collectionRepository;
		this.auditService = auditService;
	}

	// --------------------------------------------------------------------- ADD
	@Transactional
	public PaymentEntryResponse add(AddPaymentRequest req, User user) {

		// STEP 1: validate the input
		if (req.chitNo() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "chitNo is required");
		}
		LocalDate date = checkDate(req.paidDate());
		BigDecimal amount = checkAmount(req.amount());
		String remark = checkRemark(req.remark());

		// STEP 2: the member
		Chit chit = chitRepository.findById(req.chitNo())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chit " + req.chitNo() + " not found"));
		if (!"ACTIVE".equals(chit.getStatus())) {
			throw new ApiException(HttpStatus.CONFLICT, "Chit " + chit.getChitNo() + " is " + chit.getStatus());
		}

		// STEP 3: the month of this date must be open
		requireOpenMonth(date, chit.getChitNo());

		// STEP 4: save
		ChitPayment p = new ChitPayment();
		p.setChitNo(chit.getChitNo());
		p.setPaidDate(date);
		p.setAmount(amount);
		p.setRemark(remark);
		p.setCreatedBy(user.getId());
		p = paymentRepository.save(p);

		// STEP 5: audit
		auditService.logSuccess(user, "ADD_PAYMENT", "chit_payments", String.valueOf(p.getId()), null,
				values(p), "Chit " + p.getChitNo() + ": received " + p.getAmount().toPlainString() + " on "
						+ p.getPaidDate());
		return PaymentEntryResponse.from(p, chit);
	}

	// ------------------------------------------------------------------ UPDATE
	@Transactional
	public PaymentEntryResponse update(UpdatePaymentRequest req, User user) {
		if (req.id() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "id is required");
		}
		ChitPayment p = paymentRepository.findById(req.id())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment " + req.id() + " not found"));

		// the old month must be open (a locked month is final)
		requireOpenMonth(p.getPaidDate(), null);
		Map<String, Object> oldValues = values(p);

		if (req.paidDate() != null) {
			LocalDate date = checkDate(req.paidDate());
			requireOpenMonth(date, p.getChitNo());
			p.setPaidDate(date);
		}
		if (req.amount() != null) {
			p.setAmount(checkAmount(req.amount()));
		}
		if (req.remark() != null) {
			p.setRemark(checkRemark(req.remark()));
		}
		p.setUpdatedBy(user.getId());
		p = paymentRepository.save(p);

		auditService.logSuccess(user, "UPDATE_PAYMENT_ENTRY", "chit_payments", String.valueOf(p.getId()),
				oldValues, values(p), "Chit " + p.getChitNo() + ": payment " + p.getId() + " changed");
		return PaymentEntryResponse.from(p, chitRepository.findById(p.getChitNo()).orElse(null));
	}

	// ------------------------------------------------------------------ DELETE
	@Transactional
	public void delete(PaymentIdRequest req, User user) {
		if (req.id() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "id is required");
		}
		ChitPayment p = paymentRepository.findById(req.id())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment " + req.id() + " not found"));
		requireOpenMonth(p.getPaidDate(), null);

		Map<String, Object> oldValues = values(p);
		paymentRepository.delete(p);

		// the deleted values stay in the audit log
		auditService.logSuccess(user, "DELETE_PAYMENT", "chit_payments", String.valueOf(req.id()), oldValues, null,
				"Chit " + p.getChitNo() + ": payment " + req.id() + " of " + p.getAmount().toPlainString()
						+ " deleted");
	}

	// -------------------------------------------------------------------- LIST
	@Transactional(readOnly = true)
	public PaymentListResponse list(PaymentListRequest req) {
		if (req.year() == null || req.month() == null || req.month() < 1 || req.month() > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "year and month (1 to 12) are required");
		}
		LocalDate from = LocalDate.of(req.year(), req.month(), 1);
		LocalDate to = from.plusMonths(1);

		List<ChitPayment> payments = paymentRepository.findInRange(from, to).stream()
				.filter(p -> req.chitNo() == null || p.getChitNo().equals(req.chitNo()))
				.filter(p -> req.date() == null || p.getPaidDate().equals(req.date()))
				.toList();

		Set<Integer> chitNos = new HashSet<>();
		for (ChitPayment p : payments) {
			chitNos.add(p.getChitNo());
		}
		Map<Integer, Chit> chits = new HashMap<>();
		for (Chit c : chitRepository.findAllById(chitNos)) {
			chits.put(c.getChitNo(), c);
		}

		BigDecimal total = BigDecimal.ZERO;
		List<PaymentEntryResponse> out = new java.util.ArrayList<>();
		for (ChitPayment p : payments) {
			total = total.add(p.getAmount());
			out.add(PaymentEntryResponse.from(p, chits.get(p.getChitNo())));
		}

		String status = sheetRepository.findBySheetYearAndSheetMonth(req.year(), req.month())
				.map(MonthlySheet::getStatus).orElse("NO_SHEET");
		return new PaymentListResponse(req.year(), req.month(), status, out.size(), total, out);
	}

	// ----------------------------------------------------------------- helpers
	/**
	 * The month of the date must be open for receipts:
	 *  - a LOCKED month is final;
	 *  - a DRAFT month must contain the chit (when chitNo is given);
	 *  - a month with no sheet is allowed only when it is the month right after the latest sheet
	 *    (the sheet is generated after the previous month is locked).
	 */
	public void requireOpenMonth(LocalDate date, Integer chitNo) {
		int year = date.getYear();
		int month = date.getMonthValue();
		String period = year + "-" + String.format("%02d", month);

		Optional<MonthlySheet> latest = sheetRepository.findTopByOrderBySheetYearDescSheetMonthDesc();
		if (latest.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "Generate the first month before adding payments");
		}

		Optional<MonthlySheet> sheet = sheetRepository.findBySheetYearAndSheetMonth(year, month);
		if (sheet.isPresent()) {
			if (!"DRAFT".equals(sheet.get().getStatus())) {
				throw new ApiException(HttpStatus.CONFLICT, "Month " + period + " is " + sheet.get().getStatus()
						+ ". Payments dated in it cannot be added, changed or deleted. "
						+ "Use a date in the open month and add a remark");
			}
			if (chitNo != null && collectionRepository.findBySheetIdAndChitNo(sheet.get().getId(), chitNo).isEmpty()) {
				throw new ApiException(HttpStatus.NOT_FOUND, "Chit " + chitNo + " is not in the " + period + " sheet");
			}
			return;
		}

		int latestIndex = latest.get().getSheetYear() * 12 + (latest.get().getSheetMonth() - 1);
		int index = year * 12 + (month - 1);
		if (index < latestIndex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "There is no sheet for " + period
					+ " (it is before the first month). Older payments go into the member's openingPaid");
		}
		if (index > latestIndex + 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "paidDate " + date + " is too far ahead");
		}
		// index == latestIndex + 1: the next month, whose sheet is not generated yet - allowed
	}

	private LocalDate checkDate(LocalDate date) {
		if (date == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "paidDate is required");
		}
		if (date.isAfter(LocalDate.now())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "paidDate cannot be in the future");
		}
		return date;
	}

	private BigDecimal checkAmount(BigDecimal amount) {
		if (amount == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount is required");
		}
		if (amount.signum() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be greater than 0");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount can have at most 2 decimal places");
		}
		if (amount.compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount is too large");
		}
		return amount;
	}

	private String checkRemark(String remark) {
		if (remark == null || remark.isBlank()) {
			return null;
		}
		String r = remark.trim();
		if (r.length() > 500) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "remark can have at most 500 characters");
		}
		return r;
	}

	private Map<String, Object> values(ChitPayment p) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("chitNo", p.getChitNo());
		m.put("paidDate", p.getPaidDate());
		m.put("amount", p.getAmount());
		m.put("remark", p.getRemark());
		return m;
	}
}
