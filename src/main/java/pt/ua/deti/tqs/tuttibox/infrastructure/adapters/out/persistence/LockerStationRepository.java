package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;

import java.util.Optional;

@Repository
public interface LockerStationRepository extends JpaRepository<LockerStation, Long> {
    Optional<LockerStation> findByStationCode(String stationCode);
}
