package sudar.chits.sheet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sudar.chits.sheet.entity.MonthlySheet;

public interface MonthlySheetRepository extends JpaRepository<MonthlySheet, Integer> {

	boolean existsByStatus(String status);

	Optional<MonthlySheet> findFirstByStatus(String status);

	Optional<MonthlySheet> findTopByOrderBySheetYearDescSheetMonthDesc();

	Optional<MonthlySheet> findBySheetYearAndSheetMonth(Integer sheetYear, Integer sheetMonth);
}
