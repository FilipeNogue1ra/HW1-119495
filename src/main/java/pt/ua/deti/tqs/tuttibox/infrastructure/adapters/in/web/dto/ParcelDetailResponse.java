package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import pt.ua.deti.tqs.tuttibox.core.model.Actor;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;

import java.time.Instant;
import java.util.List;

public class ParcelDetailResponse {

    private String trackingCode;
    private Dimension dimension;
    private ParcelState state;
    private String stationCode;
    private String stationName;
    private String compartmentNumber;
    private Instant dropoffDeadline;
    private Instant pickupDeadline;
    private List<TransitionDto> transitions;

    public record TransitionDto(
            ParcelState fromState,
            ParcelState toState,
            Actor actor,
            Instant timestamp,
            String reason
    ) {}

    public ParcelDetailResponse() {
    }

    public ParcelDetailResponse(String trackingCode, Dimension dimension, ParcelState state,
                                String stationCode, String stationName, String compartmentNumber,
                                Instant dropoffDeadline, Instant pickupDeadline,
                                List<TransitionDto> transitions) {
        this.trackingCode = trackingCode;
        this.dimension = dimension;
        this.state = state;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.compartmentNumber = compartmentNumber;
        this.dropoffDeadline = dropoffDeadline;
        this.pickupDeadline = pickupDeadline;
        this.transitions = transitions;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public ParcelState getState() {
        return state;
    }

    public String getStationCode() {
        return stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public String getCompartmentNumber() {
        return compartmentNumber;
    }

    public Instant getDropoffDeadline() {
        return dropoffDeadline;
    }

    public Instant getPickupDeadline() {
        return pickupDeadline;
    }

    public List<TransitionDto> getTransitions() {
        return transitions;
    }
}
