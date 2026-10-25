package sudar.chits.plan.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sudar.chits.plan.dto.PlanResponse;
import sudar.chits.plan.service.PlanService;
import sudar.chits.user.service.CurrentUserService;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

	private final PlanService planService;
	private final CurrentUserService currentUserService;

	public PlanController(PlanService planService, CurrentUserService currentUserService) {
		this.planService = planService;
		this.currentUserService = currentUserService;
	}

	// read only: caller must be a valid user, not audited
	@PostMapping("/list")
	public ResponseEntity<List<PlanResponse>> list() {
		currentUserService.requireUser();
		return ResponseEntity.ok(planService.listPlans());
	}
}
