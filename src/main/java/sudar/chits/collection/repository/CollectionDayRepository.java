package sudar.chits.collection.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sudar.chits.collection.entity.CollectionDay;

public interface CollectionDayRepository extends JpaRepository<CollectionDay, Integer> {

	Optional<CollectionDay> findByDayDate(LocalDate dayDate);

	/** Saved days with startDate <= day < endDate. */
	@Query("select d from CollectionDay d where d.dayDate >= :startDate and d.dayDate < :endDate order by d.dayDate")
	List<CollectionDay> findInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
