package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pt.ua.deti.tqs.tuttibox.core.model.Compartment;
import pt.ua.deti.tqs.tuttibox.core.model.CompartmentState;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompartmentRepository extends JpaRepository<Compartment, Long> {

    List<Compartment> findByStation(LockerStation station);

    Optional<Compartment> findByStationAndCompartmentNumber(LockerStation station, String compartmentNumber);

    List<Compartment> findByStationAndState(LockerStation station, CompartmentState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compartment c WHERE c.station = :station AND c.state = :state AND c.dimension = :dimension ORDER BY c.compartmentNumber ASC")
    List<Compartment> findAvailableForUpdate(
            @Param("station") LockerStation station,
            @Param("state") CompartmentState state,
            @Param("dimension") Dimension dimension
    );
}
