package sudar.chits.payment.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sudar.chits.payment.entity.ChitPayment;

public interface ChitPaymentRepository extends JpaRepository<ChitPayment, Integer> {

	/** All receipts with startDate <= date < endDate, oldest first. */
	@Query("select p from ChitPayment p where p.paidDate >= :startDate and p.paidDate < :endDate order by p.paidDate, p.id")
	List<ChitPayment> findInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	/** Total received per chit for every receipt dated before :beforeDate. Each row is [chitNo, total]. */
	@Query("select p.chitNo, sum(p.amount) from ChitPayment p where p.paidDate < :beforeDate group by p.chitNo")
	List<Object[]> sumByChitBefore(@Param("beforeDate") LocalDate beforeDate);
}
