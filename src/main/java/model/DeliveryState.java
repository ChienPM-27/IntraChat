package model;

public enum DeliveryState {
    SENT("Đã gửi"), DELIVERED("Đã nhận"), SEEN("Đã xem");

    private final String label;

    DeliveryState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public DeliveryState advanceTo(DeliveryState state) {
        return compareTo(state) >= 0 ? this : state;
    }
}
