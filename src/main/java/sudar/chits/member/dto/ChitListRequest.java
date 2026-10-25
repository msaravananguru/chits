package sudar.chits.member.dto;

public record ChitListRequest(String status) {   // optional: ACTIVE / CLOSED, empty = all
}
