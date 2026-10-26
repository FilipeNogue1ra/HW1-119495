package pt.ua.deti.tqs.tuttibox.core.model;

import jakarta.persistence.*;

@Entity
@Table(name = "compartments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"station_id", "compartment_number"})
})
public class Compartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private LockerStation station;

    @Column(name = "compartment_number", nullable = false)
    private String compartmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Dimension dimension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompartmentState state = CompartmentState.FREE;

    @Column(name = "maintenance_note")
    private String maintenanceNote;

    @Version
    private Long version;

    public Compartment() {
    }

    public Compartment(LockerStation station, String compartmentNumber, Dimension dimension) {
        this.station = station;
        this.compartmentNumber = compartmentNumber;
        this.dimension = dimension;
        this.state = CompartmentState.FREE;
    }

    public Long getId() {
        return id;
    }

    public LockerStation getStation() {
        return station;
    }

    public String getCompartmentNumber() {
        return compartmentNumber;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public CompartmentState getState() {
        return state;
    }

    public void setState(CompartmentState state) {
        this.state = state;
    }

    public String getMaintenanceNote() {
        return maintenanceNote;
    }

    public void setMaintenanceNote(String maintenanceNote) {
        this.maintenanceNote = maintenanceNote;
    }

    public Long getVersion() {
        return version;
    }
}
