package sudar.chits.sheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.audit.service.AuditService;
import sudar.chits.common.exception.ApiException;
import sudar.chits.member.entity.Chit;
import sudar.chits.member.repository.ChitRepository;
import sudar.chits.payment.entity.ChitPayment;
import sudar.chits.payment.repository.ChitPaymentRepository;
import sudar.chits.plan.entity.ChitPlan;
import sudar.chits.plan.repository.ChitPlanRepository;
import sudar.chits.sheet.dto.SheetListItem;
import sudar.chits.sheet.dto.SheetResponse;
import sudar.chits.sheet.dto.SheetRowResponse;
import sudar.chits.sheet.dto.SheetTotalsResponse;
import sudar.chits.sheet.dto.SheetPeriodRequest;
import sudar.chits.sheet.dto.SheetSummaryResponse;
import sudar.chits.sheet.entity.ChitCollection;
import sudar.chits.sheet.entity.MonthlySheet;
import sudar.chits.sheet.repository.ChitCollectionRepository;
import sudar.chits.sheet.repository.MonthlySheetRepository;
import sudar.chits.user.entity.User;

@Service
public class SheetService {

	private final MonthlySheetRepository sheetRepository;
	private final ChitCollectionRepository collectionRepository;
	private final ChitRepository chitRepository;
	private final ChitPlanRepository chitPlanRepository;
	private final ChitPaymentRepository paymentRepository;
	private final AuditService auditService;

	public SheetService(MonthlySheetRepository sheetRepository, ChitCollectionRepository collectionRepository,
			ChitRepository chitRepository, ChitPlanRepository chitPlanRepository,
			ChitPaymentRepository paymentRepository, AuditService auditService) {
		this.sheetRepository = sheetRepository;
		this.collectionRepository = collectionRepository;
		this.chitRepository = chitRepository;
		this.chitPlanRepository = chitPlanRepository;
		this.paymentRepository = paymentRepository;
		this.auditService = auditService;
	}

	// ---------------------------------------------------------------- GENERATE
	@Transactional
	public SheetSummaryResponse generate(SheetPeriodRequest req, User user) {

		// STEP 1: validate the year and month
		if (req.year() == null || req.year() < 2000 || req.year() > 2100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "year is required (2000 to 2100)");
		}
		if (req.month() == null || req.month() < 1 || req.month() > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "month is required (1 to 12)");
		}
		int year = req.year();
		int month = req.month();

		// STEP 2: only one open (DRAFT) month at a time
		if (sheetRepository.existsByStatus("DRAFT")) {
			throw new ApiException(HttpStatus.CONFLICT,
					"A month is still in DRAFT. Lock it before generating the next month");
		}

		// STEP 3: must be exactly the month after the latest sheet (no gaps, no going back)
		Optional<MonthlySheet> latest = sheetRepository.findTopByOrderBySheetYearDescSheetMonthDesc();
		if (latest.isPresent()) {
			int latestIndex = latest.get().getSheetYear() * 12 + (latest.get().getSheetMonth() - 1);
			int requestedIndex = year * 12 + (month - 1);
			int nextIndex = latestIndex + 1;
			if (requestedIndex != nextIndex) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"The next month to generate is " + (nextIndex / 12) + "-" + String.format("%02d", nextIndex % 12 + 1));
			}
		}

		// STEP 4: create the sheet as DRAFT
		MonthlySheet sheet = new MonthlySheet();
		sheet.setSheetYear(year);
		sheet.setSheetMonth(month);
		sheet.setStatus("DRAFT");
		sheet.setCreatedBy(user.getId());
		sheet = sheetRepository.save(sheet);

		// STEP 5: one row for every active chit that is running in this month
		Map<String, ChitPlan> plans = new HashMap<>();
		for (ChitPlan p : chitPlanRepository.findAll()) {
			plans.put(key(p.getPlanAmount()), p);
		}

		List<ChitCollection> rows = new ArrayList<>();
		int skipped = 0;
		for (Chit chit : chitRepository.findByStatusOrderByChitNoAsc("ACTIVE")) {
			ChitPlan plan = plans.get(key(chit.getPlanAmount()));
			if (plan == null) {
				throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
						"Plan not found for chit " + chit.getChitNo());
			}

			// month 1 = the month the member joined
			int paidMonth = (year - chit.getJoinedDate().getYear()) * 12
					+ (month - chit.getJoinedDate().getMonthValue()) + 1;
			if (paidMonth < 1 || paidMonth > plan.getTotalMonths()) {
				skipped++;
				continue;
			}

			// up to (and including) the taken month: lower amount; after it: higher amount
			boolean afterTaken = chit.getTakenMonth() != null && paidMonth > chit.getTakenMonth();
			BigDecimal due = afterTaken ? plan.getMonthlyAfterTaken() : plan.getMonthlyBeforeTaken();

			ChitCollection row = new ChitCollection();
			row.setSheetId(sheet.getId());
			row.setChitNo(chit.getChitNo());
			row.setPaidMonth(paidMonth);
			row.setDueAmount(due);
			row.setPaidAmount(BigDecimal.ZERO);
			row.setCreatedBy(user.getId());
			rows.add(row);
		}
		if (rows.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "No chits are running in " + year + "-" + month);
		}
		collectionRepository.saveAll(rows);

		// STEP 6: audit
		Map<String, Object> newValues = new LinkedHashMap<>();
		newValues.put("year", year);
		newValues.put("month", month);
		newValues.put("status", "DRAFT");
		newValues.put("rowsCreated", rows.size());
		newValues.put("skipped", skipped);
		auditService.logSuccess(user, "GENERATE_MONTH", "monthly_sheet", String.valueOf(sheet.getId()),
				null, newValues, "Month " + year + "-" + String.format("%02d", month) + " generated with "
						+ rows.size() + " rows");

		return SheetSummaryResponse.from(sheet, rows.size(), skipped);
	}

	// ------------------------------------------------- ADD A NEW CHIT TO THE OPEN MONTH
	/**
	 * A chit added while a month is open (DRAFT) gets its row in that month right away, with the same
	 * rules as "generate". Without it the member would not appear in Collection until the next month.
	 * Does nothing (returns false) when no month is open, the chit is not ACTIVE, it is not running in
	 * that month (not joined yet / finished), or it already has a row. Runs inside the caller's transaction.
	 */
	@Transactional
	public boolean addChitToOpenSheet(Chit chit, User user) {
		if (!"ACTIVE".equals(chit.getStatus())) {
			return false;
		}
		Optional<MonthlySheet> open = sheetRepository.findFirstByStatus("DRAFT");
		if (open.isEmpty()) {
			return false;
		}
		MonthlySheet sheet = open.get();
		if (collectionRepository.findBySheetIdAndChitNo(sheet.getId(), chit.getChitNo()).isPresent()) {
			return false;
		}
		ChitPlan plan = chitPlanRepository.findById(chit.getPlanAmount()).orElse(null);
		if (plan == null) {
			return false;
		}
		int paidMonth = (sheet.getSheetYear() - chit.getJoinedDate().getYear()) * 12
				+ (sheet.getSheetMonth() - chit.getJoinedDate().getMonthValue()) + 1;
		if (paidMonth < 1 || paidMonth > plan.getTotalMonths()) {
			return false;
		}
		boolean afterTaken = chit.getTakenMonth() != null && paidMonth > chit.getTakenMonth();

		ChitCollection row = new ChitCollection();
		row.setSheetId(sheet.getId());
		row.setChitNo(chit.getChitNo());
		row.setPaidMonth(paidMonth);
		row.setDueAmount(afterTaken ? plan.getMonthlyAfterTaken() : plan.getMonthlyBeforeTaken());
		row.setPaidAmount(BigDecimal.ZERO);
		row.setCreatedBy(user.getId());
		collectionRepository.save(row);

		Map<String, Object> newValues = new LinkedHashMap<>();
		newValues.put("chitNo", chit.getChitNo());
		newValues.put("paidMonth", paidMonth);
		newValues.put("dueAmount", row.getDueAmount());
		auditService.logSuccess(user, "ADD_CHIT_TO_OPEN_MONTH", "chit_collections", String.valueOf(sheet.getId()), null,
				newValues, "Chit " + chit.getChitNo() + " added to the open month "
						+ sheet.getSheetYear() + "-" + String.format("%02d", sheet.getSheetMonth()));
		return true;
	}

	// --------------------------------------------------------------------- LOCK
	@Transactional
	public SheetResponse lock(SheetPeriodRequest req, User user) {

		// STEP 1: validate the year and month
		if (req.year() == null || req.month() == null || req.month() < 1 || req.month() > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "year and month (1 to 12) are required");
		}
		String period = req.year() + "-" + String.format("%02d", req.month());

		// STEP 2: find the sheet; only a DRAFT month can be locked
		MonthlySheet sheet = sheetRepository.findBySheetYearAndSheetMonth(req.year(), req.month())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sheet " + period + " not found"));
		if (!"DRAFT".equals(sheet.getStatus())) {
			throw new ApiException(HttpStatus.CONFLICT, "Month " + period + " is already " + sheet.getStatus());
		}

		// STEP 3: calculate every row exactly as the get API does (the month is still DRAFT)
		SheetResponse calculated = getSheet(req);
		Map<Integer, SheetRowResponse> byChit = new HashMap<>();
		for (SheetRowResponse r : calculated.rows()) {
			byChit.put(r.chitNo(), r);
		}

		// STEP 4: save the calculated values into the rows
		List<ChitCollection> rows = collectionRepository.findBySheetIdOrderByChitNo(sheet.getId());
		for (ChitCollection c : rows) {
			SheetRowResponse r = byChit.get(c.getChitNo());
			c.setPaidAmount(r.paidAmount());
			c.setPaidDate(r.paidDate());
			c.setRemark(r.remark());
			c.setTotalPaid(r.totalPaid());
			c.setTotalDueAmount(r.totalDueAmount());
			c.setMonthsPending(r.monthsPending());
			c.setRemainingAmount(r.remainingAmount());
			c.setUpdatedBy(user.getId());
		}
		collectionRepository.saveAll(rows);

		// STEP 5: make the month read-only (final)
		sheet.setStatus("LOCKED");
		sheet.setLockedAt(LocalDateTime.now());
		sheet.setLockedBy(user.getId());
		sheetRepository.save(sheet);

		// STEP 6: audit
		Map<String, Object> oldValues = new LinkedHashMap<>();
		oldValues.put("status", "DRAFT");
		Map<String, Object> newValues = new LinkedHashMap<>();
		newValues.put("status", "LOCKED");
		newValues.put("rows", rows.size());
		newValues.put("paidAmount", calculated.totals().paidAmount());
		newValues.put("remainingAmount", calculated.totals().remainingAmount());
		auditService.logSuccess(user, "LOCK_MONTH", "monthly_sheet", String.valueOf(sheet.getId()), oldValues,
				newValues, "Month " + period + " locked with " + rows.size() + " rows");

		return getSheet(req);
	}

	// --------------------------------------------------------------------- LIST
	@Transactional(readOnly = true)
	public List<SheetListItem> listSheets() {
		Sort newestFirst = Sort.by(Sort.Direction.DESC, "sheetYear").and(Sort.by(Sort.Direction.DESC, "sheetMonth"));
		return sheetRepository.findAll(newestFirst).stream().map(SheetListItem::from).toList();
	}

	// ----------------------------------------------------------------------- GET
	@Transactional(readOnly = true)
	public SheetResponse getSheet(SheetPeriodRequest req) {

		// STEP 1: validate the year and month
		if (req.year() == null || req.month() == null || req.month() < 1 || req.month() > 12) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "year and month (1 to 12) are required");
		}
		String period = req.year() + "-" + String.format("%02d", req.month());

		// STEP 2: find the sheet and its rows
		MonthlySheet sheet = sheetRepository.findBySheetYearAndSheetMonth(req.year(), req.month())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sheet " + period + " not found"));
		List<ChitCollection> rows = collectionRepository.findBySheetIdOrderByChitNo(sheet.getId());

		// STEP 3: receipts - this month's list per chit, and the total received up to the end of this month
		LocalDate monthStart = LocalDate.of(sheet.getSheetYear(), sheet.getSheetMonth(), 1);
		LocalDate nextMonthStart = monthStart.plusMonths(1);
		Map<Integer, List<ChitPayment>> monthPayments = paymentRepository.findInRange(monthStart, nextMonthStart)
				.stream().collect(Collectors.groupingBy(ChitPayment::getChitNo));
		Map<Integer, BigDecimal> receivedUpToNow = new HashMap<>();
		for (Object[] r : paymentRepository.sumByChitBefore(nextMonthStart)) {
			receivedUpToNow.put((Integer) r[0], (BigDecimal) r[1]);
		}

		// STEP 4: members and plans
		HashSet<Integer> chitNos = new HashSet<>();
		for (ChitCollection c : rows) {
			chitNos.add(c.getChitNo());
		}
		Map<Integer, Chit> chits = new HashMap<>();
		for (Chit c : chitRepository.findAllById(chitNos)) {
			chits.put(c.getChitNo(), c);
		}
		Map<String, ChitPlan> plans = new HashMap<>();
		for (ChitPlan p : chitPlanRepository.findAll()) {
			plans.put(key(p.getPlanAmount()), p);
		}

		// STEP 5: build every row and the footer totals
		boolean locked = "LOCKED".equals(sheet.getStatus());
		List<SheetRowResponse> out = new ArrayList<>();
		BigDecimal sumDue = BigDecimal.ZERO;
		BigDecimal sumPaid = BigDecimal.ZERO;
		BigDecimal sumTotalPaid = BigDecimal.ZERO;
		BigDecimal sumTotalDue = BigDecimal.ZERO;
		BigDecimal sumRemaining = BigDecimal.ZERO;

		for (ChitCollection c : rows) {
			Chit chit = chits.get(c.getChitNo());
			ChitPlan plan = chit == null ? null : plans.get(key(chit.getPlanAmount()));
			if (chit == null || plan == null) {
				throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
						"Member or plan not found for chit " + c.getChitNo());
			}

			List<ChitPayment> mine = monthPayments.getOrDefault(c.getChitNo(), List.of());
			BigDecimal paidAmount;
			LocalDate paidDate;
			String remark;
			BigDecimal totalPaid;
			BigDecimal totalDue;
			BigDecimal remaining;
			BigDecimal pending;
			if (locked && c.getTotalPaid() != null) {
				// a locked month shows the values saved when it was locked
				paidAmount = c.getPaidAmount();
				paidDate = c.getPaidDate();
				remark = c.getRemark();
				totalPaid = c.getTotalPaid();
				totalDue = c.getTotalDueAmount();
				remaining = c.getRemainingAmount();
				pending = c.getMonthsPending();
			} else {
				paidAmount = BigDecimal.ZERO;
				paidDate = null;
				StringBuilder notes = new StringBuilder();
				for (ChitPayment p : mine) {
					paidAmount = paidAmount.add(p.getAmount());
					if (paidDate == null || p.getPaidDate().isAfter(paidDate)) {
						paidDate = p.getPaidDate();
					}
					if (p.getRemark() != null && notes.indexOf(p.getRemark()) < 0) {
						notes.append(notes.length() > 0 ? "; " : "").append(p.getRemark());
					}
				}
				remark = notes.length() == 0 ? null : (notes.length() > 500 ? notes.substring(0, 500) : notes.toString());
				totalPaid = chit.getOpeningPaid().add(receivedUpToNow.getOrDefault(c.getChitNo(), BigDecimal.ZERO));
				Integer taken = chit.getTakenMonth();
				BigDecimal before = plan.getMonthlyBeforeTaken();
				BigDecimal after = plan.getMonthlyAfterTaken();
				BigDecimal months = BigDecimal.valueOf(plan.getTotalMonths());
				if (taken == null) {
					// not taken: assume it will never be taken (all months at the lower amount)
					totalDue = before.multiply(months);
				} else {
					BigDecimal takenMonths = BigDecimal.valueOf(taken);
					totalDue = before.multiply(takenMonths).add(after.multiply(months.subtract(takenMonths)));
				}
				remaining = totalDue.subtract(totalPaid);
				BigDecimal rate = taken == null ? before : after;
				pending = rate.signum() > 0 ? remaining.divide(rate, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
			}

			out.add(new SheetRowResponse(c.getChitNo(), chit.getMemberName(), chit.getMemberNameTamil(),
					chit.getPlanAmount(), chit.getTakenMonth(), c.getPaidMonth(), c.getDueAmount(),
					paidAmount, paidDate, remark, mine.size(), totalPaid, totalDue, pending, remaining,
					chit.getPayMode(), chit.getDailyAmount()));

			sumDue = sumDue.add(c.getDueAmount());
			sumPaid = sumPaid.add(paidAmount);
			sumTotalPaid = sumTotalPaid.add(totalPaid);
			sumTotalDue = sumTotalDue.add(totalDue);
			sumRemaining = sumRemaining.add(remaining);
		}

		SheetTotalsResponse totals = new SheetTotalsResponse(out.size(), sumDue, sumPaid, sumTotalPaid,
				sumTotalDue, sumRemaining);
		return new SheetResponse(sheet.getId(), sheet.getSheetYear(), sheet.getSheetMonth(), sheet.getStatus(),
				sheet.getLockedAt(), out, totals);
	}

	private int monthIndex(MonthlySheet s) {
		return s.getSheetYear() * 12 + (s.getSheetMonth() - 1);
	}

	// 100000.00 and 100000 must count as the same plan
	private String key(BigDecimal amount) {
		return amount.stripTrailingZeros().toPlainString();
	}
}
