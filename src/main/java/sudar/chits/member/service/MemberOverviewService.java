package sudar.chits.member.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.common.exception.ApiException;
import sudar.chits.member.dto.MemberOverviewResponse;
import sudar.chits.member.dto.MemberOverviewRow;
import sudar.chits.member.dto.MemberOverviewSummary;
import sudar.chits.member.entity.Chit;
import sudar.chits.member.repository.ChitRepository;
import sudar.chits.payment.entity.ChitPayment;
import sudar.chits.payment.repository.ChitPaymentRepository;
import sudar.chits.plan.entity.ChitPlan;
import sudar.chits.plan.repository.ChitPlanRepository;
import sudar.chits.sheet.dto.SheetListItem;
import sudar.chits.sheet.dto.SheetPeriodRequest;
import sudar.chits.sheet.dto.SheetResponse;
import sudar.chits.sheet.dto.SheetRowResponse;
import sudar.chits.sheet.entity.MonthlySheet;
import sudar.chits.sheet.repository.MonthlySheetRepository;
import sudar.chits.sheet.service.PreviousBalanceService;
import sudar.chits.sheet.service.SheetService;

/**
 * Members page data for one month.
 * - A LOCKED month is read from the saved master rows (nothing is recalculated) = FINAL.
 * - A DRAFT month (or a month with no sheet yet) is calculated now from the members and receipts = LIVE.
 */
@Service
public class MemberOverviewService {

	private final SheetService sheetService;
	private final MonthlySheetRepository sheetRepository;
	private final ChitRepository chitRepository;
	private final ChitPlanRepository chitPlanRepository;
	private final ChitPaymentRepository paymentRepository;
	private final PreviousBalanceService previousBalanceService;

	public MemberOverviewService(SheetService sheetService, MonthlySheetRepository sheetRepository,
			ChitRepository chitRepository, ChitPlanRepository chitPlanRepository,
			ChitPaymentRepository paymentRepository, PreviousBalanceService previousBalanceService) {
		this.sheetService = sheetService;
		this.sheetRepository = sheetRepository;
		this.chitRepository = chitRepository;
		this.chitPlanRepository = chitPlanRepository;
		this.paymentRepository = paymentRepository;
		this.previousBalanceService = previousBalanceService;
	}

	@Transactional(readOnly = true)
	public MemberOverviewResponse overview(SheetPeriodRequest req) {

		// STEP 1: the months that have a sheet (newest first)
		List<SheetListItem> months = sheetService.listSheets();

		// STEP 2: which month? default = newest sheet, or the current calendar month if there is none yet
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
		String period = year + "-" + String.format("%02d", month);

		// STEP 3: the sheet of that month (it may not exist only when no sheet exists at all)
		Optional<MonthlySheet> sheetOpt = sheetRepository.findBySheetYearAndSheetMonth(year, month);
		if (sheetOpt.isEmpty() && !months.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Sheet " + period + " not found");
		}
		String sheetStatus = sheetOpt.map(MonthlySheet::getStatus).orElse("NONE");
		boolean locked = "LOCKED".equals(sheetStatus);

		// STEP 4: members and plans
		Map<Integer, Chit> chits = new HashMap<>();
		for (Chit c : chitRepository.findAll(Sort.by("chitNo"))) {
			chits.put(c.getChitNo(), c);
		}
		Map<String, ChitPlan> plans = new HashMap<>();
		for (ChitPlan p : chitPlanRepository.findAll()) {
			plans.put(key(p.getPlanAmount()), p);
		}

		// previous balance (+ advance, - arrears) of every member at the start of the month
		Map<Integer, BigDecimal> prev = previousBalanceService.forMonth(year, month, chits.values());

		List<MemberOverviewRow> rows = new ArrayList<>();
		Set<Integer> inSheet = new HashSet<>();

		// STEP 5: chits that are in the sheet - exactly the sheet's own figures (saved ones when locked)
		if (sheetOpt.isPresent()) {
			SheetResponse sheet = sheetService.getSheet(new SheetPeriodRequest(year, month));
			for (SheetRowResponse r : sheet.rows()) {
				Chit chit = chits.get(r.chitNo());
				ChitPlan plan = chit == null ? null : plans.get(key(chit.getPlanAmount()));
				if (chit == null || plan == null) {
					throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
							"Member or plan not found for chit " + r.chitNo());
				}
				inSheet.add(r.chitNo());

				// a locked month shows the taken status as it was in that month
				Integer taken = chit.getTakenMonth();
				if (locked && taken != null && taken > r.paidMonth()) {
					taken = null;
				}
				BigDecimal pb = prev.getOrDefault(r.chitNo(), BigDecimal.ZERO);
				rows.add(new MemberOverviewRow(r.chitNo(), chit.getMemberName(), chit.getMemberNameTamil(),
						chit.getPlanAmount(), chit.getStatus(), taken, r.paidMonth(), plan.getTotalMonths(),
						r.paidMonth() > plan.getTotalMonths(), r.dueAmount(), r.paidAmount(),
						payStatus(r.dueAmount(), pb, r.paidAmount()), r.totalPaid(), r.totalDueAmount(),
						r.remainingAmount(), chit.getPayMode(), chit.getDailyAmount(), pb,
						toPay(r.dueAmount(), pb, r.paidAmount())));
			}
		}

		// STEP 6: chits that are not in the sheet - calculated now with the same rules
		//         (closed chits, and chits added after the month was generated)
		LocalDate monthStart = LocalDate.of(year, month, 1);
		LocalDate nextMonthStart = monthStart.plusMonths(1);
		Map<Integer, BigDecimal> receivedInMonth = new HashMap<>();
		for (ChitPayment p : paymentRepository.findInRange(monthStart, nextMonthStart)) {
			receivedInMonth.merge(p.getChitNo(), p.getAmount(), BigDecimal::add);
		}
		Map<Integer, BigDecimal> receivedUpToNow = new HashMap<>();
		for (Object[] r : paymentRepository.sumByChitBefore(nextMonthStart)) {
			receivedUpToNow.put((Integer) r[0], (BigDecimal) r[1]);
		}

		for (Chit chit : chits.values()) {
			if (inSheet.contains(chit.getChitNo())) {
				continue;
			}
			boolean closed = "CLOSED".equals(chit.getStatus());
			if (locked && !closed) {
				continue;   // a locked month never grows: later joiners are not part of it
			}
			ChitPlan plan = plans.get(key(chit.getPlanAmount()));
			if (plan == null) {
				throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
						"Plan not found for chit " + chit.getChitNo());
			}
			int chitMonth = (year - chit.getJoinedDate().getYear()) * 12
					+ (month - chit.getJoinedDate().getMonthValue()) + 1;
			if (chitMonth < 1) {
				continue;   // has not joined yet in that month
			}
			rows.add(liveRow(chit, plan, chitMonth, receivedInMonth.getOrDefault(chit.getChitNo(), BigDecimal.ZERO),
					chit.getOpeningPaid().add(receivedUpToNow.getOrDefault(chit.getChitNo(), BigDecimal.ZERO)),
					prev.getOrDefault(chit.getChitNo(), BigDecimal.ZERO)));
		}

		rows.sort(Comparator.comparing(MemberOverviewRow::chitNo));

		// STEP 7: numbers for the stat cards (active chits only)
		int active = 0;
		int taken = 0;
		int paid = 0;
		BigDecimal totalPaid = BigDecimal.ZERO;
		for (MemberOverviewRow r : rows) {
			if (!"ACTIVE".equals(r.status())) {
				continue;
			}
			active++;
			if (r.takenMonth() != null) {
				taken++;
			}
			if ("PAID".equals(r.monthStatus())) {
				paid++;
			}
			totalPaid = totalPaid.add(r.totalPaid());
		}

		return new MemberOverviewResponse(year, month, locked ? "FINAL" : "LIVE", sheetStatus, months,
				new MemberOverviewSummary(active, taken, paid, totalPaid), rows);
	}

	// same formulas as the monthly sheet
	private MemberOverviewRow liveRow(Chit chit, ChitPlan plan, int chitMonth, BigDecimal paidInMonth,
			BigDecimal totalPaid, BigDecimal previousBalance) {
		Integer taken = chit.getTakenMonth();
		BigDecimal before = plan.getMonthlyBeforeTaken();
		BigDecimal after = plan.getMonthlyAfterTaken();
		BigDecimal months = BigDecimal.valueOf(plan.getTotalMonths());

		boolean completed = chitMonth > plan.getTotalMonths();
		BigDecimal due;
		if (completed) {
			due = BigDecimal.ZERO;
		} else {
			boolean afterTaken = taken != null && chitMonth > taken;
			due = afterTaken ? after : before;
		}

		BigDecimal totalDue;
		if (taken == null) {
			totalDue = before.multiply(months);
		} else {
			BigDecimal takenMonths = BigDecimal.valueOf(taken);
			totalDue = before.multiply(takenMonths).add(after.multiply(months.subtract(takenMonths)));
		}
		BigDecimal remaining = totalDue.subtract(totalPaid);

		return new MemberOverviewRow(chit.getChitNo(), chit.getMemberName(), chit.getMemberNameTamil(),
				chit.getPlanAmount(), chit.getStatus(), taken, chitMonth, plan.getTotalMonths(), completed,
				due, paidInMonth, payStatus(due, previousBalance, paidInMonth), totalPaid, totalDue, remaining,
				chit.getPayMode(), chit.getDailyAmount(), previousBalance, toPay(due, previousBalance, paidInMonth));
	}

	/** What the member still has to pay now: due, plus arrears (or minus advance), minus received. Never negative. */
	private BigDecimal toPay(BigDecimal due, BigDecimal previousBalance, BigDecimal paid) {
		return due.subtract(previousBalance).subtract(paid).max(BigDecimal.ZERO);
	}

	/** PAID when nothing is left to pay now (also when an advance covers the due), PART / NOT_PAID otherwise. */
	private String payStatus(BigDecimal due, BigDecimal previousBalance, BigDecimal paid) {
		if (due.signum() <= 0 && previousBalance.signum() >= 0) {
			return "NONE";
		}
		if (toPay(due, previousBalance, paid).signum() == 0) {
			return "PAID";
		}
		return paid.signum() > 0 ? "PART" : "NOT_PAID";
	}

	// 100000.00 and 100000 must count as the same plan
	private String key(BigDecimal amount) {
		return amount.stripTrailingZeros().toPlainString();
	}
}
