package pt.ua.deti.tqs.tuttibox.infrastructure.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.tuttibox.core.model.*;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final LockerStationRepository stationRepo;
    private final CompartmentRepository compartmentRepo;

    public DataInitializer(LockerStationRepository stationRepo, CompartmentRepository compartmentRepo) {
        this.stationRepo = stationRepo;
        this.compartmentRepo = compartmentRepo;
    }

    @Override
    public void run(String... args) {
        if (stationRepo.count() > 0) {
            return;
        }

        // 1. Estação CP Aveiro
        LockerStation s1 = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        stationRepo.save(s1);
        addCompartment(s1, "A01", Dimension.S, CompartmentState.FREE);
        addCompartment(s1, "A02", Dimension.S, CompartmentState.OCCUPIED);
        addCompartment(s1, "B01", Dimension.M, CompartmentState.FREE);
        addCompartment(s1, "B02", Dimension.M, CompartmentState.RESERVED);
        addCompartment(s1, "C01", Dimension.L, CompartmentState.FREE);
        addCompartment(s1, "C02", Dimension.L, CompartmentState.OUT_OF_SERVICE);

        // 2. Universidade de Aveiro
        LockerStation s2 = new LockerStation("STATION-AVEIRO-02", "Universidade de Aveiro", "Campus Universitário de Santiago", "Aveiro", 40.6306, -8.6575);
        stationRepo.save(s2);
        addCompartment(s2, "A01", Dimension.S, CompartmentState.FREE);
        addCompartment(s2, "A02", Dimension.S, CompartmentState.FREE);
        addCompartment(s2, "B01", Dimension.M, CompartmentState.OCCUPIED);
        addCompartment(s2, "B02", Dimension.M, CompartmentState.FREE);
        addCompartment(s2, "C01", Dimension.L, CompartmentState.FREE);

        // 3. Fórum Aveiro
        LockerStation s3 = new LockerStation("STATION-AVEIRO-03", "Fórum Aveiro", "Rua Batalhão de Caçadores 10", "Aveiro", 40.6412, -8.6515);
        stationRepo.save(s3);
        addCompartment(s3, "A01", Dimension.S, CompartmentState.FREE);
        addCompartment(s3, "B01", Dimension.M, CompartmentState.FREE);
        addCompartment(s3, "B02", Dimension.M, CompartmentState.OCCUPIED);
        addCompartment(s3, "C01", Dimension.L, CompartmentState.OUT_OF_SERVICE);

        // 4. Hospital de Aveiro
        LockerStation s4 = new LockerStation("STATION-AVEIRO-04", "Hospital Infante D. Pedro", "Avenida Artur Ravara", "Aveiro", 40.6358, -8.6472);
        stationRepo.save(s4);
        addCompartment(s4, "A01", Dimension.S, CompartmentState.FREE);
        addCompartment(s4, "B01", Dimension.M, CompartmentState.FREE);
        addCompartment(s4, "C01", Dimension.L, CompartmentState.FREE);

        // 5. Glicínias Plaza
        LockerStation s5 = new LockerStation("STATION-AVEIRO-05", "Glicínias Plaza", "Rua D. Manuel Barbuda e Vasconcelos", "Aveiro", 40.6272, -8.6455);
        stationRepo.save(s5);
        addCompartment(s5, "A01", Dimension.S, CompartmentState.FREE);
        addCompartment(s5, "A02", Dimension.S, CompartmentState.OCCUPIED);
        addCompartment(s5, "B01", Dimension.M, CompartmentState.FREE);
        addCompartment(s5, "C01", Dimension.L, CompartmentState.FREE);

        // 6. Cais da Fonte Nova
        LockerStation s6 = new LockerStation("STATION-AVEIRO-06", "Cais da Fonte Nova", "Rua do Cais da Fonte Nova", "Aveiro", 40.6402, -8.6441);
        stationRepo.save(s6);
        addCompartment(s6, "A01", Dimension.S, CompartmentState.FREE);
        addCompartment(s6, "B01", Dimension.M, CompartmentState.FREE);
        addCompartment(s6, "C01", Dimension.L, CompartmentState.FREE);
    }

    private void addCompartment(LockerStation station, String number, Dimension dim, CompartmentState state) {
        Compartment c = new Compartment(station, number, dim);
        c.setState(state);
        if (state == CompartmentState.OUT_OF_SERVICE) {
            c.setMaintenanceNote("Sensor com leituras intermitentes.");
        }
        compartmentRepo.save(c);
    }
}
