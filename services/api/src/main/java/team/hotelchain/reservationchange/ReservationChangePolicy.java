package team.hotelchain.reservationchange;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReservationChangePolicy {
    private final boolean settlementEnabled;
    private final Duration approvalTtl;
    private final Duration holdTtl;
    private final team.hotelchain.policy.CurrentPolicy currentPolicy;

    public ReservationChangePolicy(
            @Value("${reservation.change.settlement-enabled:false}") boolean settlementEnabled,
            @Value("${reservation.change.approval-ttl:24h}") Duration approvalTtl,
            @Value("${reservation.change.hold-ttl:15m}") Duration holdTtl,
            team.hotelchain.policy.CurrentPolicy currentPolicy) {
        this.settlementEnabled = settlementEnabled;
        this.approvalTtl = approvalTtl;
        this.holdTtl = holdTtl;
        this.currentPolicy = currentPolicy;
    }

    // 본사가 정책을 변경하면 DB의 현재값을 쓴다. 변경 전에는 코드·application.yml 기본값을 쓴다.
    public long directLimitKrw() {
        return currentPolicy.changeApprovalDirectLimitKrw();
    }

    public boolean settlementEnabled() {
        return settlementEnabled;
    }

    public Duration approvalTtl() {
        return approvalTtl;
    }

    public Duration holdTtl() {
        return holdTtl;
    }

    public Instant approvalExpiresAt(Instant createdAt, LocalDate checkIn, String timezone) {
        Instant ttlExpiry = createdAt.plus(approvalTtl);
        Instant checkInCutoff = checkIn.atStartOfDay(ZoneId.of(timezone)).toInstant();
        return ttlExpiry.isBefore(checkInCutoff) ? ttlExpiry : checkInCutoff;
    }
}
