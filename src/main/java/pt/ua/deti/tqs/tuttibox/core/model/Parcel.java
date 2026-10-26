package pt.ua.deti.tqs.tuttibox.core.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parcels")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", unique = true, nullable = false)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Dimension dimension;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParcelState state = ParcelState.CREATED;

    @Column(name = "pickup_pin_hash", nullable = false)
    private String pickupPinHash;

    @Column(name = "dropoff_deadline", nullable = false)
    private Instant dropoffDeadline;

    @Column(name = "pickup_deadline")
    private Instant pickupDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compartment_id")
    private Compartment compartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private LockerStation station;

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParcelTransition> transitions = new ArrayList<>();

    @Version
    private Long version;

    public Parcel() {
    }

    public Parcel(String trackingCode, Dimension dimension, String recipientPhone, String recipientEmail,
                  String pickupPinHash, Instant dropoffDeadline, LockerStation station, Compartment compartment) {
        this.trackingCode = trackingCode;
        this.dimension = dimension;
        this.recipientPhone = recipientPhone;
        this.recipientEmail = recipientEmail;
        this.pickupPinHash = pickupPinHash;
        this.dropoffDeadline = dropoffDeadline;
        this.station = station;
        this.compartment = compartment;
        this.state = ParcelState.CREATED;
    }

    public Long getId() {
        return id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public ParcelState getState() {
        return state;
    }

    public void setState(ParcelState state) {
        this.state = state;
    }

    public String getPickupPinHash() {
        return pickupPinHash;
    }

    public void setPickupPinHash(String pickupPinHash) {
        this.pickupPinHash = pickupPinHash;
    }

    public Instant getDropoffDeadline() {
        return dropoffDeadline;
    }

    public Instant getPickupDeadline() {
        return pickupDeadline;
    }

    public void setPickupDeadline(Instant pickupDeadline) {
        this.pickupDeadline = pickupDeadline;
    }

    public Compartment getCompartment() {
        return compartment;
    }

    public void setCompartment(Compartment compartment) {
        this.compartment = compartment;
    }

    public LockerStation getStation() {
        return station;
    }

    public List<ParcelTransition> getTransitions() {
        return transitions;
    }

    public void addTransition(ParcelState toState, Actor actor, Instant timestamp, String reason) {
        ParcelTransition transition = new ParcelTransition(this, this.state, toState, actor, timestamp, reason);
        this.transitions.add(transition);
        this.state = toState;
    }
}
