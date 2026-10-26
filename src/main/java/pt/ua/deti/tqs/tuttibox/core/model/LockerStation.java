package pt.ua.deti.tqs.tuttibox.core.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "locker_stations")
public class LockerStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", unique = true, nullable = false)
    private String stationCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "consecutive_failed_pins", nullable = false)
    private int consecutiveFailedPins = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Compartment> compartments = new ArrayList<>();

    public LockerStation() {
    }

    public LockerStation(String stationCode, String name, String address, String city, double latitude, double longitude) {
        this.stationCode = stationCode;
        this.name = name;
        this.address = address;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public String getStationCode() {
        return stationCode;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getConsecutiveFailedPins() {
        return consecutiveFailedPins;
    }

    public void setConsecutiveFailedPins(int consecutiveFailedPins) {
        this.consecutiveFailedPins = consecutiveFailedPins;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public List<Compartment> getCompartments() {
        return compartments;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void recordFailedPinAttempt(Instant now, int maxAttempts, int lockoutSeconds) {
        this.consecutiveFailedPins++;
        if (this.consecutiveFailedPins >= maxAttempts) {
            this.lockedUntil = now.plusSeconds(lockoutSeconds);
            this.consecutiveFailedPins = 0;
        }
    }

    public void resetFailedPins() {
        this.consecutiveFailedPins = 0;
        this.lockedUntil = null;
    }
}
