package sudar.chits.sheet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sudar.chits.sheet.entity.ChitCollection;

public interface ChitCollectionRepository extends JpaRepository<ChitCollection, Integer> {

	Optional<ChitCollection> findBySheetIdAndChitNo(Integer sheetId, Integer chitNo);

	List<ChitCollection> findBySheetIdOrderByChitNo(Integer sheetId);

	List<ChitCollection> findBySheetIdIn(Collection<Integer> sheetIds);

	/**
	 * The due amount of every sheet row of the months BEFORE the given month index (year * 12 + month).
	 * Each row is [chitNo, sheetYear, sheetMonth, dueAmount]. Used for the "previous balance".
	 */
	@Query("select c.chitNo, s.sheetYear, s.sheetMonth, c.dueAmount from ChitCollection c, MonthlySheet s "
			+ "where c.sheetId = s.id and (s.sheetYear * 12 + s.sheetMonth) < :monthIndex")
	List<Object[]> findDueBefore(@Param("monthIndex") int monthIndex);
}
