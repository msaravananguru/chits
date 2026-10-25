package sudar.chits.collection.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.audit.service.AuditService;
import sudar.chits.collection.dto.AllListResponse;
import sudar.chits.collection.dto.AllRow;
import sudar.chits.collection.dto.AllSummary;
import sudar.chits.collection.dto.DailyGridResponse;
import sudar.chits.collection.dto.DailyGridRow;
import sudar.chits.collection.dto.SaveDayEntry;
import sudar.chits.collection.dto.SaveDayRequest;
import sudar.chits.collection.dto.SaveDayResponse;
import sudar.chits.collection.dto.SingleListResponse;
import sudar.chits.collection.dto.SingleRow;
import sudar.chits.collection.dto.SingleSummary;
import sudar.chits.collection.entity.CollectionDay;
import sudar.chits.collection.repository.CollectionDayRepository;
import sudar.chits.common.exception.ApiException;
import sudar.chits.member.entity.Chit;
import sudar.chits.member.repository.ChitRepository;
import sudar.chits.payment.entity.ChitPayment;
import sudar.chits.payment.repository.ChitPaymentRepository;
import sudar.chits.payment.service.PaymentService;
import sudar.chits.sheet.dto.SheetListItem;
import sudar.chits.sheet.dto.SheetPeriodRequest;
import sudar.chits.sheet.dto.SheetRowResponse;
import sudar.chits.sheet.entity.MonthlySheet;
import sudar.chits.sheet.repository.MonthlySheetRepository;
import sudar.chits.sheet.service.PreviousBalanceService;
import sudar.chits.sheet.service.SheetService;
import sudar.chits.user.entity.User;

/**
 * Collection page: the Daily grid (DLC style), saving a whole day at once, and the Single list.
 * Money is always stored as receipts in chit_payments; this service only reads and writes them.
 */
@Service
public class CollectionService {

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
	private static final int MAX_ENTRIES = 500;

	private final SheetService sheetService;
	private final MonthlySheetRepository sheetRepository;
	private final ChitPaymentRepository paymentRepository;
	private final ChitRepository chitRepository;
	private final CollectionDayRepository dayRepository;
	private final PaymentService paymentService;
	private final AuditService auditService;
	private final PreviousBalanceService previousBalanceService;

	public CollectionService(SheetService sheetService, MonthlySheetRepository sheetRepository,
			ChitPaymentRepository paymentRepository, ChitRepository chitRepository,
			CollectionDayRepository dayRepository, PaymentService paymentService, AuditService auditService,
			PreviousBalanceService previousBalanceService) {
		this.sheetService = sheetService;
		this.sheetRepository = sheetRepository;
		this.paymentRepository = paymentRepository;
		this.chitRepository = chitRepository;
		this.dayRepository = dayRepository;
		this.paymentService = paymentService;
		this.auditService = auditService;
		this.previousBalanceService = previousBalanceService;
	}

	// ------------------------------------------------------------------- DAILY
	@Transactional(readOnly = true)
	public DailyGridResponse daily(SheetPeriodRequest req) {
		Period p = resolve(req);
		LocalDate start = LocalDate.of(p.year(), p.month(), 1);
		LocalDate next = start.plusMonths(1);
		int daysInMonth = start.lengthOfMonth();
		List<SheetRowResponse> sheetRows = sheetRows(p);

		// receipts of the month: total and count per member per day
		Map<Integer, BigDecimal[]> amounts = new HashMap<>();
		Map<Integer, Integer[]> counts = new HashMap<>();
		for (ChitPayment pay : paymentRepository.findInRange(start, next)) {
			int d = pay.getPaidDate().getDayOfMonth() - 1;
			BigDecimal[] a = amounts.computeIfAbsent(pay.getChitNo(), k -> zeros(daysInMonth));
			a[d] = a[d].add(pay.getAmount());
			Integer[] c = counts.computeIfAbsent(pay.getChitNo(), k -> zeroInts(daysInMonth));
			c[d] = c[d] + 1;
		}

		Map<Integer, BigDecimal> prev = previousBalances(p, sheetRows);

		int dailyCount = 0;
		int singleCount = 0;
		List<DailyGridRow> rows = new ArrayList<>();
		BigDecimal[] dayTotals = zeros(daysInMonth);
		for (SheetRowResponse r : sheetRows) {
			if (!isDaily(r)) {
				singleCount++;
				continue;
			}
			dailyCount++;
			BigDecimal[] a = amounts.getOrDefault(r.chitNo(), zeros(daysInMonth));
			Integer[] c = counts.getOrDefault(r.chitNo(), zeroInts(daysInMonth));
			for (int i = 0; i < daysInMonth; i++) {
				dayTotals[i] = dayTotals[i].add(a[i]);
			}
			BigDecimal pb = prev.getOrDefault(r.chitNo(), BigDecimal.ZERO);
			rows.add(new DailyGridRow(r.chitNo(), r.memberName(), r.memberNameTamil(), r.takenMonth(),
					r.dailyAmount(), r.dueAmount(), r.paidAmount(), r.dueAmount().subtract(r.paidAmount()), pb,
					toPay(r.dueAmount(), pb, r.paidAmount()), List.of(a), List.of(c)));
		}
		rows.sort(Comparator.comparing(DailyGridRow::chitNo));

		List<Integer> savedDays = dayRepository.findInRange(start, next).stream()
				.map(d -> d.getDayDate().getDayOfMonth()).toList();
		BigDecimal monthTotal = BigDecimal.ZERO;
		for (BigDecimal t : dayTotals) {
			monthTotal = monthTotal.add(t);
		}

		String status = p.status();
		return new DailyGridResponse(p.year(), p.month(), source(status), status, "DRAFT".equals(status),
				LocalDate.now(), daysInMonth, dailyCount, singleCount, p.months(), savedDays, rows,
				List.of(dayTotals), monthTotal);
	}

	// ---------------------------------------------------------------- SAVE DAY
	@Transactional
	public SaveDayResponse saveDay(SaveDayRequest req, User user) {

		// STEP 1: validate the input
		if (req == null || req.date() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "date is required");
		}
		LocalDate date = req.date();
		if (date.isAfter(LocalDate.now())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "date cannot be in the future");
		}
		if (req.entries() == null || req.entries().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "entries are required");
		}
		if (req.entries().size() > MAX_ENTRIES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "At most " + MAX_ENTRIES + " entries can be saved at once");
		}
		String remark = checkRemark(req.remark());
		Set<Integer> seen = new HashSet<>();
		for (SaveDayEntry e : req.entries()) {
			if (e == null || e.chitNo() == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "chitNo is required in every entry");
			}
			if (!seen.add(e.chitNo())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Chit " + e.chitNo() + " is listed twice");
			}
			checkAmount(e.chitNo(), e.amount());
		}

		// STEP 2: the month of this date must be open (a locked month is final)
		paymentService.requireOpenMonth(date, null);

		// STEP 3: members and the receipts that already exist on this day
		Map<Integer, Chit> chits = new HashMap<>();
		for (Chit c : chitRepository.findAllById(seen)) {
			chits.put(c.getChitNo(), c);
		}
		for (Integer chitNo : seen) {
			if (!chits.containsKey(chitNo)) {
				throw new ApiException(HttpStatus.NOT_FOUND, "Chit " + chitNo + " not found");
			}
		}
		Map<Integer, List<ChitPayment>> existing = paymentRepository.findInRange(date, date.plusDays(1)).stream()
				.collect(Collectors.groupingBy(ChitPayment::getChitNo));

		// STEP 4: make every member's total for the day equal to what was entered
		int added = 0;
		int updated = 0;
		int removed = 0;
		int unchanged = 0;
		BigDecimal dayTotal = BigDecimal.ZERO;
		for (SaveDayEntry e : req.entries()) {
			BigDecimal wanted = e.amount();
			dayTotal = dayTotal.add(wanted);
			List<ChitPayment> mine = existing.getOrDefault(e.chitNo(), List.of());
			BigDecimal current = BigDecimal.ZERO;
			for (ChitPayment p : mine) {
				current = current.add(p.getAmount());
			}
			if (current.compareTo(wanted) == 0) {
				unchanged++;
				continue;
			}
			if (mine.size() > 1) {
				throw new ApiException(HttpStatus.CONFLICT, "Chit " + e.chitNo() + " has " + mine.size()
						+ " receipts on " + date + ". Change them one by one in Payments");
			}
			Chit chit = chits.get(e.chitNo());

			if (mine.isEmpty()) {
				// wanted > 0 here (current is 0 and they differ)
				if (!"ACTIVE".equals(chit.getStatus())) {
					throw new ApiException(HttpStatus.CONFLICT, "Chit " + chit.getChitNo() + " is " + chit.getStatus());
				}
				paymentService.requireOpenMonth(date, chit.getChitNo());
				ChitPayment p = new ChitPayment();
				p.setChitNo(chit.getChitNo());
				p.setPaidDate(date);
				p.setAmount(wanted);
				p.setRemark(remark);
				p.setCreatedBy(user.getId());
				p = paymentRepository.save(p);
				auditService.logSuccess(user, "ADD_PAYMENT", "chit_payments", String.valueOf(p.getId()), null,
						values(p), "Chit " + p.getChitNo() + ": received " + p.getAmount().toPlainString() + " on "
								+ p.getPaidDate() + " (daily collection)");
				added++;
			} else if (wanted.signum() == 0) {
				ChitPayment p = mine.get(0);
				Map<String, Object> oldValues = values(p);
				paymentRepository.delete(p);
				auditService.logSuccess(user, "DELETE_PAYMENT", "chit_payments", String.valueOf(p.getId()), oldValues,
						null, "Chit " + p.getChitNo() + ": payment " + p.getId() + " of "
								+ p.getAmount().toPlainString() + " removed (daily collection set to 0)");
				removed++;
			} else {
				ChitPayment p = mine.get(0);
				Map<String, Object> oldValues = values(p);
				p.setAmount(wanted);
				if (remark != null) {
					p.setRemark(remark);
				}
				p.setUpdatedBy(user.getId());
				p = paymentRepository.save(p);
				auditService.logSuccess(user, "UPDATE_PAYMENT_ENTRY", "chit_payments", String.valueOf(p.getId()),
						oldValues, values(p), "Chit " + p.getChitNo() + ": payment " + p.getId() + " changed (daily collection)");
				updated++;
			}
		}

		// STEP 5: remember that this day was saved (so 0 is shown as 0, not as "not entered")
		Optional<CollectionDay> found = dayRepository.findByDayDate(date);
		CollectionDay day = found.orElseGet(CollectionDay::new);
		if (found.isEmpty()) {
			day.setDayDate(date);
			day.setSaveCount(1);
			day.setCreatedBy(user.getId());
		} else {
			day.setSaveCount(day.getSaveCount() + 1);
		}
		day.setUpdatedBy(user.getId());
		dayRepository.save(day);

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("date", date);
		summary.put("added", added);
		summary.put("updated", updated);
		summary.put("removed", removed);
		summary.put("unchanged", unchanged);
		summary.put("dayTotal", dayTotal);
		auditService.logSuccess(user, "SAVE_COLLECTION_DAY", "collection_days", date.toString(), null, summary,
				"Daily collection saved for " + date + ": " + added + " added, " + updated + " changed, " + removed
						+ " removed");

		return new SaveDayResponse(date, added, updated, removed, unchanged, dayTotal);
	}

	// ------------------------------------------------------------------ SINGLE
	@Transactional(readOnly = true)
	public SingleListResponse single(SheetPeriodRequest req) {
		Period p = resolve(req);
		List<SheetRowResponse> sheetRows = sheetRows(p);
		Map<Integer, BigDecimal> prev = previousBalances(p, sheetRows);

		int dailyCount = 0;
		int singleCount = 0;
		int paidCount = 0;
		BigDecimal collected = BigDecimal.ZERO;
		BigDecimal toCollect = BigDecimal.ZERO;
		List<SingleRow> rows = new ArrayList<>();
		for (SheetRowResponse r : sheetRows) {
			if (isDaily(r)) {
				dailyCount++;
				continue;
			}
			singleCount++;
			BigDecimal balance = r.dueAmount().subtract(r.paidAmount()).max(BigDecimal.ZERO);
			BigDecimal pb = prev.getOrDefault(r.chitNo(), BigDecimal.ZERO);
			BigDecimal toPay = toPay(r.dueAmount(), pb, r.paidAmount());
			String status = payStatus(r.dueAmount(), pb, r.paidAmount());
			if ("PAID".equals(status)) {
				paidCount++;
			}
			collected = collected.add(r.paidAmount());
			toCollect = toCollect.add(toPay);
			rows.add(new SingleRow(r.chitNo(), r.memberName(), r.memberNameTamil(), r.takenMonth(), r.dueAmount(),
					r.paidAmount(), balance, pb, toPay, r.paidDate(), r.paymentCount(), status));
		}
		rows.sort(Comparator.comparing(SingleRow::chitNo));

		String status = p.status();
		return new SingleListResponse(p.year(), p.month(), source(status), status, "DRAFT".equals(status),
				LocalDate.now(), dailyCount, singleCount, p.months(),
				new SingleSummary(singleCount, paidCount, collected, toCollect), rows);
	}

	// --------------------------------------------------------------------- ALL
	/** Every member of the month (Daily and Single) with the basic data. View only. */
	@Transactional(readOnly = true)
	public AllListResponse all(SheetPeriodRequest req) {
		Period p = resolve(req);
		List<SheetRowResponse> sheetRows = sheetRows(p);
		Map<Integer, BigDecimal> prev = previousBalances(p, sheetRows);

		int dailyCount = 0;
		int singleCount = 0;
		int paidCount = 0;
		BigDecimal collected = BigDecimal.ZERO;
		BigDecimal toCollect = BigDecimal.ZERO;
		BigDecimal dueTotal = BigDecimal.ZERO;
		BigDecimal arrears = BigDecimal.ZERO;
		BigDecimal advance = BigDecimal.ZERO;
		BigDecimal dailyCollected = BigDecimal.ZERO;
		BigDecimal singleCollected = BigDecimal.ZERO;
		List<AllRow> rows = new ArrayList<>();
		for (SheetRowResponse r : sheetRows) {
			boolean daily = isDaily(r);
			BigDecimal balance = r.dueAmount().subtract(r.paidAmount()).max(BigDecimal.ZERO);
			BigDecimal pb = prev.getOrDefault(r.chitNo(), BigDecimal.ZERO);
			BigDecimal toPay = toPay(r.dueAmount(), pb, r.paidAmount());
			String status = payStatus(r.dueAmount(), pb, r.paidAmount());
			dueTotal = dueTotal.add(r.dueAmount());
			if (pb.signum() < 0) {
				arrears = arrears.add(pb.negate());
			} else {
				advance = advance.add(pb);
			}
			if (daily) {
				dailyCount++;
				dailyCollected = dailyCollected.add(r.paidAmount());
			} else {
				singleCount++;
				singleCollected = singleCollected.add(r.paidAmount());
			}
			if ("PAID".equals(status)) {
				paidCount++;
			}
			collected = collected.add(r.paidAmount());
			toCollect = toCollect.add(toPay);
			rows.add(new AllRow(r.chitNo(), r.memberName(), r.memberNameTamil(), r.takenMonth(),
					daily ? "DAILY" : "SINGLE", daily ? r.dailyAmount() : null, r.dueAmount(), r.paidAmount(),
					balance, pb, toPay, r.paidDate(), r.paymentCount(), status));
		}
		rows.sort(Comparator.comparing(AllRow::chitNo));

		String status = p.status();
		return new AllListResponse(p.year(), p.month(), source(status), status, "DRAFT".equals(status),
				LocalDate.now(), dailyCount, singleCount, p.months(),
				new AllSummary(rows.size(), paidCount, collected, toCollect, dailyCollected, singleCollected, dueTotal,
						arrears, advance), rows);
	}

	// ----------------------------------------------------------------- helpers
	private record Period(int year, int month, List<SheetListItem> months, Optional<MonthlySheet> sheet) {
		String status() {
			return sheet.map(MonthlySheet::getStatus).orElse("NONE");
		}
	}

	/** Default month = newest sheet (the open month). With no sheet at all, the current calendar month. */
	private Period resolve(SheetPeriodRequest req) {
		List<SheetListItem> months = sheetService.listSheets();
		Integer year = req == null ? null : req.year();
		Integer month = req == null ? null : req.month();
		if (year == null && month == null) {
			if (months.isEmpty()) {
				LocalDate now = LocalDate.now();
				year = now.getYear();
				month = now.getMonthValue();
			} else {
				year = months.get(0).year();
				month = months.get(0).month();
			}
		}
		if (year == null || month == null || year < 2000 || year > 2100 || month < 1 || month > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "year (2000 to 2100) and month (1 to 12) are required together");
		}
		Optional<MonthlySheet> sheet = sheetRepository.findBySheetYearAndSheetMonth(year, month);
		if (sheet.isEmpty() && !months.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Sheet " + year + "-" + String.format("%02d", month) + " not found");
		}
		return new Period(year, month, months, sheet);
	}

	private List<SheetRowResponse> sheetRows(Period p) {
		if (p.sheet().isEmpty()) {
			return List.of();
		}
		return sheetService.getSheet(new SheetPeriodRequest(p.year(), p.month())).rows();
	}

	private boolean isDaily(SheetRowResponse r) {
		return "DAILY".equals(r.payMode());
	}

	private String source(String status) {
		if ("LOCKED".equals(status)) {
			return "FINAL";
		}
		return "NONE".equals(status) ? "NONE" : "LIVE";
	}

	/** Previous balance (+ advance, - arrears) of every chit in the sheet, at the start of the month. */
	private Map<Integer, BigDecimal> previousBalances(Period p, List<SheetRowResponse> sheetRows) {
		if (sheetRows.isEmpty()) {
			return Map.of();
		}
		Set<Integer> chitNos = sheetRows.stream().map(SheetRowResponse::chitNo).collect(Collectors.toSet());
		return previousBalanceService.forMonth(p.year(), p.month(), chitRepository.findAllById(chitNos));
	}

	/** What the member still has to pay now: this month's due, plus arrears (or minus advance), minus received. Never negative. */
	static BigDecimal toPay(BigDecimal due, BigDecimal previousBalance, BigDecimal paid) {
		return due.subtract(previousBalance).subtract(paid).max(BigDecimal.ZERO);
	}

	/** PAID when nothing is left to pay now (also when an advance covers the due), PART / NOT_PAID otherwise. */
	static String payStatus(BigDecimal due, BigDecimal previousBalance, BigDecimal paid) {
		if (due.signum() <= 0 && previousBalance.signum() >= 0) {
			return "NONE";
		}
		if (toPay(due, previousBalance, paid).signum() == 0) {
			return "PAID";
		}
		return paid.signum() > 0 ? "PART" : "NOT_PAID";
	}

	private BigDecimal[] zeros(int n) {
		BigDecimal[] a = new BigDecimal[n];
		java.util.Arrays.fill(a, BigDecimal.ZERO);
		return a;
	}

	private Integer[] zeroInts(int n) {
		Integer[] a = new Integer[n];
		java.util.Arrays.fill(a, 0);
		return a;
	}

	private void checkAmount(Integer chitNo, BigDecimal amount) {
		if (amount == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount is required for chit " + chitNo + " (use 0 for nothing paid)");
		}
		if (amount.signum() < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount of chit " + chitNo + " cannot be negative");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount of chit " + chitNo + " can have at most 2 decimal places");
		}
		if (amount.compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount of chit " + chitNo + " is too large");
		}
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
