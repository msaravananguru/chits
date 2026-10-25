package sudar.chits.member.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.audit.service.AuditService;
import sudar.chits.common.exception.ApiException;
import sudar.chits.member.dto.ChitResponse;
import sudar.chits.member.dto.CreateChitRequest;
import sudar.chits.member.dto.MarkTakenRequest;
import sudar.chits.member.dto.UpdateChitRequest;
import sudar.chits.member.entity.Chit;
import sudar.chits.member.repository.ChitRepository;
import sudar.chits.plan.entity.ChitPlan;
import sudar.chits.plan.repository.ChitPlanRepository;
import sudar.chits.sheet.service.SheetService;
import sudar.chits.user.entity.User;

@Service
public class ChitService {

	private final ChitRepository chitRepository;
	private final ChitPlanRepository chitPlanRepository;
	private final AuditService auditService;
	private final SheetService sheetService;

	public ChitService(ChitRepository chitRepository, ChitPlanRepository chitPlanRepository,
			AuditService auditService, SheetService sheetService) {
		this.chitRepository = chitRepository;
		this.chitPlanRepository = chitPlanRepository;
		this.auditService = auditService;
		this.sheetService = sheetService;
	}

	// ------------------------------------------------------------------ CREATE
	@Transactional
	public ChitResponse createChit(CreateChitRequest req, User user) {

		// STEP 1: basic validation
		if (req.chitNo() == null || req.chitNo() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "chitNo is required and must be greater than 0");
		}
		if (req.memberName() == null || req.memberName().isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "memberName is required");
		}
		if (req.planAmount() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "planAmount is required");
		}
		if (req.joinedDate() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "joinedDate is required");
		}

		// STEP 2: plan must exist
		ChitPlan plan = chitPlanRepository.findById(req.planAmount())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
						"Plan not found: " + req.planAmount()));

		// STEP 3: chit number must be new
		if (chitRepository.existsById(req.chitNo())) {
			throw new ApiException(HttpStatus.CONFLICT, "Chit no " + req.chitNo() + " already exists");
		}

		// STEP 4: taken month (optional) must be within the plan's months
		Integer takenMonth = req.takenMonth();
		if (takenMonth != null && (takenMonth < 1 || takenMonth > plan.getTotalMonths())) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"takenMonth must be between 1 and " + plan.getTotalMonths());
		}

		BigDecimal openingPaid = checkOpeningPaid(req.openingPaid());
		String payMode = req.payMode() == null || req.payMode().isBlank() ? "SINGLE" : checkPayMode(req.payMode());
		BigDecimal dailyAmount = "DAILY".equals(payMode) ? checkDailyAmount(req.dailyAmount()) : null;
		if ("SINGLE".equals(payMode) && req.dailyAmount() != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "dailyAmount can be set only when payMode is DAILY");
		}

		// STEP 5: save (created_by = calling user)
		Chit chit = new Chit();
		chit.setChitNo(req.chitNo());
		chit.setMemberName(req.memberName().trim());
		chit.setMemberNameTamil(blankToNull(req.memberNameTamil()));
		chit.setPlanAmount(plan.getPlanAmount());
		chit.setJoinedDate(req.joinedDate());
		chit.setTakenMonth(takenMonth);
		chit.setOpeningPaid(openingPaid);
		chit.setPayMode(payMode);
		chit.setDailyAmount(dailyAmount);
		chit.setStatus("ACTIVE");
		chit.setCreatedBy(user.getId());
		Chit saved = chitRepository.save(chit);

		// STEP 6: audit
		auditService.logSuccess(user, "CREATE_CHIT", "chits", String.valueOf(saved.getChitNo()),
				null, snapshot(saved), "Chit " + saved.getChitNo() + " added for " + saved.getMemberName());

		// STEP 7: a month is open: the new member joins it now (otherwise Collection would not list the member)
		sheetService.addChitToOpenSheet(saved, user);

		return ChitResponse.from(saved);
	}

	// --------------------------------------------------------------------- GET
	@Transactional(readOnly = true)
	public ChitResponse getChit(Integer chitNo) {
		return ChitResponse.from(findChit(chitNo));
	}

	// -------------------------------------------------------------------- LIST
	@Transactional(readOnly = true)
	public List<ChitResponse> listChits(String status) {
		List<Chit> chits;
		if (status == null || status.isBlank()) {
			chits = chitRepository.findAll(Sort.by("chitNo"));
		} else {
			String s = status.trim().toUpperCase();
			checkStatus(s);
			chits = chitRepository.findByStatusOrderByChitNoAsc(s);
		}
		return chits.stream().map(ChitResponse::from).toList();
	}

	// ------------------------------------------------------------------ UPDATE
	@Transactional
	public ChitResponse updateChit(UpdateChitRequest req, User user) {

		// STEP 1: find the chit
		Chit chit = findChit(req.chitNo());
		Map<String, Object> oldValues = snapshot(chit);

		// STEP 2: apply only the fields that were sent
		if (req.memberName() != null) {
			if (req.memberName().isBlank()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "memberName cannot be blank");
			}
			chit.setMemberName(req.memberName().trim());
		}
		if (req.memberNameTamil() != null) {
			chit.setMemberNameTamil(blankToNull(req.memberNameTamil()));
		}
		if (req.joinedDate() != null) {
			chit.setJoinedDate(req.joinedDate());
		}
		if (req.status() != null) {
			String s = req.status().trim().toUpperCase();
			checkStatus(s);
			chit.setStatus(s);
		}
		if (req.openingPaid() != null) {
			chit.setOpeningPaid(checkOpeningPaid(req.openingPaid()));
		}
		if (req.payMode() != null) {
			// pay style sent: both values are taken as sent (SINGLE clears the daily amount)
			String mode = checkPayMode(req.payMode());
			if ("SINGLE".equals(mode) && req.dailyAmount() != null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "dailyAmount can be set only when payMode is DAILY");
			}
			chit.setPayMode(mode);
			chit.setDailyAmount("DAILY".equals(mode) ? checkDailyAmount(req.dailyAmount()) : null);
		} else if (req.dailyAmount() != null) {
			if (!"DAILY".equals(chit.getPayMode())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "dailyAmount can be set only when payMode is DAILY");
			}
			chit.setDailyAmount(checkDailyAmount(req.dailyAmount()));
		}
		chit.setUpdatedBy(user.getId());

		// STEP 3: save and audit
		Chit saved = chitRepository.save(chit);
		auditService.logSuccess(user, "UPDATE_CHIT", "chits", String.valueOf(saved.getChitNo()),
				oldValues, snapshot(saved), "Chit " + saved.getChitNo() + " updated");

		return ChitResponse.from(saved);
	}

	// ------------------------------------------------------------- MARK TAKEN
	@Transactional
	public ChitResponse markTaken(MarkTakenRequest req, User user) {

		// STEP 1: find the chit
		Chit chit = findChit(req.chitNo());
		Map<String, Object> oldValues = snapshot(chit);

		// STEP 2: validations
		if (req.takenMonth() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "takenMonth is required");
		}
		if (!"ACTIVE".equals(chit.getStatus())) {
			throw new ApiException(HttpStatus.CONFLICT, "Chit " + chit.getChitNo() + " is not ACTIVE");
		}
		if (chit.getTakenMonth() != null) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Chit " + chit.getChitNo() + " is already marked as taken in month " + chit.getTakenMonth());
		}
		ChitPlan plan = chitPlanRepository.findById(chit.getPlanAmount())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Plan not found: " + chit.getPlanAmount()));
		if (req.takenMonth() < 1 || req.takenMonth() > plan.getTotalMonths()) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"takenMonth must be between 1 and " + plan.getTotalMonths());
		}

		// STEP 3: save and audit
		chit.setTakenMonth(req.takenMonth());
		chit.setUpdatedBy(user.getId());
		Chit saved = chitRepository.save(chit);
		auditService.logSuccess(user, "MARK_TAKEN", "chits", String.valueOf(saved.getChitNo()),
				oldValues, snapshot(saved),
				"Chit " + saved.getChitNo() + " marked as taken in month " + saved.getTakenMonth());

		return ChitResponse.from(saved);
	}

	// ----------------------------------------------------------------- helpers
	private Chit findChit(Integer chitNo) {
		if (chitNo == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "chitNo is required");
		}
		return chitRepository.findById(chitNo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chit no " + chitNo + " not found"));
	}

	private void checkStatus(String status) {
		if (!"ACTIVE".equals(status) && !"CLOSED".equals(status)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or CLOSED");
		}
	}

	// amount paid before the system started: null = 0, never negative, at most 2 decimals
	private BigDecimal checkOpeningPaid(BigDecimal amount) {
		if (amount == null) {
			return BigDecimal.ZERO;
		}
		if (amount.signum() < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "openingPaid cannot be negative");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "openingPaid can have at most 2 decimal places");
		}
		if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "openingPaid is too large");
		}
		return amount;
	}

	private String checkPayMode(String mode) {
		String m = mode.trim().toUpperCase();
		if (!"SINGLE".equals(m) && !"DAILY".equals(m)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "payMode must be SINGLE or DAILY");
		}
		return m;
	}

	// usual daily amount: null = not set, otherwise greater than 0 with at most 2 decimals
	private BigDecimal checkDailyAmount(BigDecimal amount) {
		if (amount == null) {
			return null;
		}
		if (amount.signum() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "dailyAmount must be greater than 0");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "dailyAmount can have at most 2 decimal places");
		}
		if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "dailyAmount is too large");
		}
		return amount;
	}

	private String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private Map<String, Object> snapshot(Chit c) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("chitNo", c.getChitNo());
		m.put("memberName", c.getMemberName());
		m.put("memberNameTamil", c.getMemberNameTamil());
		m.put("planAmount", c.getPlanAmount());
		m.put("joinedDate", c.getJoinedDate());
		m.put("takenMonth", c.getTakenMonth());
		m.put("openingPaid", c.getOpeningPaid());
		m.put("payMode", c.getPayMode());
		m.put("dailyAmount", c.getDailyAmount());
		m.put("status", c.getStatus());
		return m;
	}
}
