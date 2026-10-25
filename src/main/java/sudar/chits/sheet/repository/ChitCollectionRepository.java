package sudar.chits.sheet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sudar.chits.sheet.entity.ChitCollection;

public interface ChitCollectionRepository extends JpaRepository<ChitCollection, Integer> {

	Optional<ChitCollection> findBySheetIdAndChitNo(Integer sheetId, Integer chitNo);

	List<ChitCollection> findBySheetIdOrderByChitNo(Integer sheetId);

	List<ChitCollection> findBySheetIdIn(Collection<Integer> sheetIds);
}
