package sudar.chits.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import sudar.chits.member.entity.Chit;

public record ChitResponse(
		Integer chitNo,
		String memberName,
		String memberNameTamil,
		BigDecimal planAmount,
		LocalDate joinedDate,
		Integer takenMonth,
		String status,
		BigDecimal openingPaid,
		String payMode,
		BigDecimal dailyAmount,
		Integer createdBy
) {
	public static ChitResponse from(Chit c) {
		return new ChitResponse(c.getChitNo(), c.getMemberName(), c.getMemberNameTamil(),
				c.getPlanAmount(), c.getJoinedDate(), c.getTakenMonth(), c.getStatus(),
				c.getOpeningPaid(), c.getPayMode(), c.getDailyAmount(), c.getCreatedBy());
	}
}
