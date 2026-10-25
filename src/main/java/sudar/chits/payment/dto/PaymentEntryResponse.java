package sudar.chits.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import sudar.chits.member.entity.Chit;
import sudar.chits.payment.entity.ChitPayment;

public record PaymentEntryResponse(
		Integer id,
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		LocalDate paidDate,
		BigDecimal amount,
		String remark,
		Integer createdBy
) {
	public static PaymentEntryResponse from(ChitPayment p, Chit chit) {
		return new PaymentEntryResponse(p.getId(), p.getChitNo(),
				chit == null ? null : chit.getMemberName(),
				chit == null ? null : chit.getMemberNameTamil(),
				p.getPaidDate(), p.getAmount(), p.getRemark(), p.getCreatedBy());
	}
}
