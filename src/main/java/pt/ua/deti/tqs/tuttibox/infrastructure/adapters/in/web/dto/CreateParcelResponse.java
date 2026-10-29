package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;

import java.time.Instant;

public class CreateParcelResponse {

    private String trackingCode;
    private String pickupPin;
    private Dimension dimension;
    private String stationCode;
    private String stationName;
    private String compartmentNumber;
    private ParcelState state;
    private Instant dropoffDeadline;

    public CreateParcelResponse() {
    }

    public CreateParcelResponse(String trackingCode, String pickupPin, Dimension dimension,
                                String stationCode, String stationName, String compartmentNumber,
                                ParcelState state, Instant dropoffDeadline) {
        this.trackingCode = trackingCode;
        this.pickupPin = pickupPin;
        this.dimension = dimension;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.compartmentNumber = compartmentNumber;
        this.state = state;
        this.dropoffDeadline = dropoffDeadline;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getPickupPin() {
        return pickupPin;
    }

    public Dimension getDimension() {
        return dimension;
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

    public ParcelState getState() {
        return state;
    }

    public Instant getDropoffDeadline() {
        return dropoffDeadline;
    }
}
