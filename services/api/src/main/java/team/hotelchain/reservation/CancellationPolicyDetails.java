package team.hotelchain.reservation;

public record CancellationPolicyDetails(int refundCutoffDaysBefore, String refundCutoffLocalTime, String timezone) {}
