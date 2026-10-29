package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.model.Parcel;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    Optional<Parcel> findByTrackingCode(String trackingCode);
    Optional<Parcel> findByTrackingCodeAndStation(String trackingCode, LockerStation station);
    Optional<Parcel> findByStationAndPickupPinHashAndState(LockerStation station, String pickupPinHash, ParcelState state);
    List<Parcel> findByStationAndState(LockerStation station, ParcelState state);
    boolean existsByTrackingCode(String trackingCode);
}
