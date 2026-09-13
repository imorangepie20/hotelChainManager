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
    private final long directLimitKrw;
    private final Duration approvalTtl;
    private final Duration holdTtl;

    public ReservationChangePolicy(
            @Value("${reservation.change.settlement-enabled:false}") boolean settlementEnabled,
            @Value("${reservation.change.direct-limit-krw:100000}") long directLimitKrw,
            @Value("${reservation.change.approval-ttl:24h}") Duration approvalTtl,
            @Value("${reservation.change.hold-ttl:15m}") Duration holdTtl) {
        this.settlementEnabled = settlementEnabled;
        this.directLimitKrw = directLimitKrw;
        this.approvalTtl = approvalTtl;
        this.holdTtl = holdTtl;
    }

    public long directLimitKrw() {
        return directLimitKrw;
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
