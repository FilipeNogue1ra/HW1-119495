package pt.ua.deti.tqs.tuttibox.core.model;

public enum ParcelState {
    CREATED,
    READY_FOR_PICKUP,
    WAITING_FOR_OPERATOR,
    PICKED_UP,
    CANCELLED,
    EXPIRED,
    RETURNED;

    public boolean isTerminal() {
        return this == PICKED_UP || this == CANCELLED || this == RETURNED;
    }
}
