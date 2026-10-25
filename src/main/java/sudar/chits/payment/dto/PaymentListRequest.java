package sudar.chits.payment.dto;

import java.time.LocalDate;

/** year and month are required. chitNo and date (one day) are optional filters. */
public record PaymentListRequest(Integer year, Integer month, Integer chitNo, LocalDate date) {
}
