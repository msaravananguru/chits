package sudar.chits.payment.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sudar.chits.payment.entity.ChitPayment;

public interface ChitPaymentRepository extends JpaRepository<ChitPayment, Integer> {

	/** All receipts with from <= date < to, oldest first. */
	@Query("select p from ChitPayment p where p.paidDate >= :from and p.paidDate < :to order by p.paidDate, p.id")
	List<ChitPayment> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Total received per chit for every receipt dated before :to. Each row is [chitNo, total]. */
	@Query("select p.chitNo, sum(p.amount) from ChitPayment p where p.paidDate < :to group by p.chitNo")
	List<Object[]> sumByChitBefore(@Param("to") LocalDate to);
}
