package sudar.chits.sheet.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.member.entity.Chit;
import sudar.chits.payment.repository.ChitPaymentRepository;
import sudar.chits.plan.entity.ChitPlan;
import sudar.chits.plan.repository.ChitPlanRepository;
import sudar.chits.sheet.repository.ChitCollectionRepository;

/**
 * "Previous balance" of a chit at the start of a month:
 *
 *   everything paid before the month (opening_paid + receipts dated before the 1st)
 *   minus everything that was due in the months before it.
 *
 *   positive = paid ahead (advance)      negative = left unpaid earlier (arrears)
 *
 * Nothing is stored: it is worked out from the receipts and the due amounts, so locked months never change.
 * A month's due is the amount saved in that month's sheet row; a month that has no sheet row (the system
 * started later than the chit) uses the plan rule (lower amount up to the taken month, higher after it).
 */
@Service
public class PreviousBalanceService {

	private final ChitPlanRepository chitPlanRepository;
	private final ChitCollectionRepository collectionRepository;
	private final ChitPaymentRepository paymentRepository;

	public PreviousBalanceService(ChitPlanRepository chitPlanRepository, ChitCollectionRepository collectionRepository,
			ChitPaymentRepository paymentRepository) {
		this.chitPlanRepository = chitPlanRepository;
		this.collectionRepository = collectionRepository;
		this.paymentRepository = paymentRepository;
	}

	/** Previous balance of each given chit at the start of year-month. Key = chit no. */
	@Transactional(readOnly = true)
	public Map<Integer, BigDecimal> forMonth(int year, int month, Collection<Chit> chits) {
		Map<String, ChitPlan> plans = new HashMap<>();
		for (ChitPlan p : chitPlanRepository.findAll()) {
			plans.put(key(p.getPlanAmount()), p);
		}
		int monthIndex = year * 12 + month;
		Map<Integer, Map<Integer, BigDecimal>> recorded = new HashMap<>();   // chit -> (month index -> due)
		for (Object[] r : collectionRepository.findDueBefore(monthIndex)) {
			int idx = ((Integer) r[1]) * 12 + ((Integer) r[2]);
			recorded.computeIfAbsent((Integer) r[0], k -> new HashMap<>()).put(idx, (BigDecimal) r[3]);
		}
		Map<Integer, BigDecimal> receivedBefore = new HashMap<>();
		for (Object[] r : paymentRepository.sumByChitBefore(LocalDate.of(year, month, 1))) {
			receivedBefore.put((Integer) r[0], (BigDecimal) r[1]);
		}

		Map<Integer, BigDecimal> out = new HashMap<>();
		for (Chit chit : chits) {
			ChitPlan plan = plans.get(key(chit.getPlanAmount()));
			if (plan == null) {
				out.put(chit.getChitNo(), BigDecimal.ZERO);
				continue;
			}
			out.put(chit.getChitNo(), compute(chit.getJoinedDate(), chit.getTakenMonth(), plan.getTotalMonths(),
					plan.getMonthlyBeforeTaken(), plan.getMonthlyAfterTaken(), chit.getOpeningPaid(),
					receivedBefore.getOrDefault(chit.getChitNo(), BigDecimal.ZERO),
					recorded.getOrDefault(chit.getChitNo(), Map.of()), year, month));
		}
		return out;
	}

	/** The calculation on its own (no database), so it can be tested. */
	static BigDecimal compute(LocalDate joined, Integer taken, int totalMonths, BigDecimal before, BigDecimal after,
			BigDecimal openingPaid, BigDecimal receivedBefore, Map<Integer, BigDecimal> recordedDue, int year,
			int month) {
		int thisIdx = year * 12 + month;                       // month index, same scale as the repository query
		int joinedIdx = joined.getYear() * 12 + joined.getMonthValue();
		BigDecimal dueBefore = BigDecimal.ZERO;
		for (int idx = joinedIdx; idx < thisIdx; idx++) {
			int chitMonth = idx - joinedIdx + 1;
			if (chitMonth > totalMonths) {
				break;
			}
			BigDecimal recordedAmount = recordedDue.get(idx);
			if (recordedAmount != null) {
				dueBefore = dueBefore.add(recordedAmount);
			} else {
				boolean afterTaken = taken != null && chitMonth > taken;
				dueBefore = dueBefore.add(afterTaken ? after : before);
			}
		}
		return openingPaid.add(receivedBefore).subtract(dueBefore);
	}

	// 100000.00 and 100000 must count as the same plan
	private String key(BigDecimal amount) {
		return amount.stripTrailingZeros().toPlainString();
	}
}
