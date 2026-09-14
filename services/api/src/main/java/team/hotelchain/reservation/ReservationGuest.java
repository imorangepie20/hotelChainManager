package team.hotelchain.reservation;

public record ReservationGuest(String name, String email, String phone) {
    public ReservationGuest(String name, String email) {
        this(name, email, null);
    }
}
