package team.hotelchain.reservationchange;

import java.util.UUID;

public record SettlementCommandClaim(
        UUID outboxId,
        UUID requestId,
        UUID attemptId,
        String commandType,
        int attemptCount,
        UUID claimToken) {
}
