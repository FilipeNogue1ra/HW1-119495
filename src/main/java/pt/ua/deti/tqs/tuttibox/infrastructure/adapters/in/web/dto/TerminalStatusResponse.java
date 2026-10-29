package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

public record TerminalStatusResponse(
        String stationCode,
        boolean locked,
        long remainingLockoutSeconds,
        int consecutiveFailedPins
) {
}
