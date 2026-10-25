package sudar.chits.plan.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;

import sudar.chits.plan.entity.ChitPlan;

public interface ChitPlanRepository extends JpaRepository<ChitPlan, BigDecimal> {
}
