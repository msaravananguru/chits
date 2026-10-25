package sudar.chits.plan.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sudar.chits.plan.dto.PlanResponse;
import sudar.chits.plan.repository.ChitPlanRepository;

@Service
public class PlanService {

	private final ChitPlanRepository chitPlanRepository;

	public PlanService(ChitPlanRepository chitPlanRepository) {
		this.chitPlanRepository = chitPlanRepository;
	}

	@Transactional(readOnly = true)
	public List<PlanResponse> listPlans() {
		return chitPlanRepository.findAll(Sort.by("planAmount")).stream().map(PlanResponse::from).toList();
	}
}
