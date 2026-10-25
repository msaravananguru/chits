package sudar.chits.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sudar.chits.member.entity.Chit;

public interface ChitRepository extends JpaRepository<Chit, Integer> {

	List<Chit> findByStatusOrderByChitNoAsc(String status);
}
