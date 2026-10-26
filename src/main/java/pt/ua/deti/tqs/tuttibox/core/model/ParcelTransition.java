package pt.ua.deti.tqs.tuttibox.core.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "parcel_transitions")
public class ParcelTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state")
    private ParcelState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private ParcelState toState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Actor actor;

    @Column(nullable = false)
    private Instant timestamp;

    @Column
    private String reason;

    public ParcelTransition() {
    }

    public ParcelTransition(Parcel parcel, ParcelState fromState, ParcelState toState, Actor actor, Instant timestamp, String reason) {
        this.parcel = parcel;
        this.fromState = fromState;
        this.toState = toState;
        this.actor = actor;
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Parcel getParcel() {
        return parcel;
    }

    public ParcelState getFromState() {
        return fromState;
    }

    public ParcelState getToState() {
        return toState;
    }

    public Actor getActor() {
        return actor;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getReason() {
        return reason;
    }
}
