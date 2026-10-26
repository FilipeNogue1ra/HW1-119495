package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.deti.tqs.tuttibox.core.model.Compartment;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompartmentRepository extends JpaRepository<Compartment, Long> {
    List<Compartment> findByStation(LockerStation station);
    Optional<Compartment> findByStationAndCompartmentNumber(LockerStation station, String compartmentNumber);
}
