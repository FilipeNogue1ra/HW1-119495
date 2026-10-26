package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.hardware;

import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.tuttibox.core.ports.out.LockerHardwarePort;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Fake implementation of the LockerHardwarePort.
 * Strictly adheres to the project guidelines (P34-P38):
 * 1. Maintains door open/closed state in memory.
 * 2. Allows configurable failure injection (e.g. simulated door jam).
 * 3. Operates as a working locker for normal demo runs and web terminal simulation.
 */
@Component
public class InMemoryHardwareFake implements LockerHardwarePort {

    // Maps stationCode:compartmentNumber -> Boolean (true = closed, false = open)
    private final Map<String, Boolean> doorClosedState = new ConcurrentHashMap<>();

    // Set of compartment keys configured to simulate a mechanical lock failure (jammed)
    private final Set<String> jammedCompartments = ConcurrentHashMap.newKeySet();

    private String key(String stationCode, String compartmentNumber) {
        return stationCode + ":" + compartmentNumber;
    }

    @Override
    public boolean unlockDoor(String stationCode, String compartmentNumber) {
        String k = key(stationCode, compartmentNumber);

        // If configured as jammed, unlocking fails (motor timeout / latch stuck)
        if (jammedCompartments.contains(k)) {
            return false;
        }

        // Normal operation: door spring pops open (state is now open, i.e., false)
        doorClosedState.put(k, false);
        return true;
    }

    @Override
    public boolean closeDoor(String stationCode, String compartmentNumber) {
        String k = key(stationCode, compartmentNumber);

        // If jammed, physical obstruction prevents closing and latching
        if (jammedCompartments.contains(k)) {
            return false;
        }

        // Normal operation: door is physically shut and latched
        doorClosedState.put(k, true);
        return true;
    }

    @Override
    public boolean isDoorClosed(String stationCode, String compartmentNumber) {
        String k = key(stationCode, compartmentNumber);
        // By default, if untouched, compartment doors are closed
        return doorClosedState.getOrDefault(k, true);
    }

    @Override
    public void resetDoor(String stationCode, String compartmentNumber) {
        String k = key(stationCode, compartmentNumber);
        jammedCompartments.remove(k);
        doorClosedState.put(k, true);
    }

    // =========================================================
    // Test & Simulation Helpers (Guia P36-P38)
    // =========================================================

    /**
     * Injects a hardware failure: simulates that this compartment door is mechanically jammed.
     * @param stationCode the station identifier
     * @param compartmentNumber the compartment number
     */
    public void simulateDoorJam(String stationCode, String compartmentNumber) {
        jammedCompartments.add(key(stationCode, compartmentNumber));
    }

    /**
     * Checks whether a compartment is currently configured with a simulated jam.
     */
    public boolean isDoorJammed(String stationCode, String compartmentNumber) {
        return jammedCompartments.contains(key(stationCode, compartmentNumber));
    }

    /**
     * Resets all internal state and clears all injected failures.
     */
    public void clearAll() {
        jammedCompartments.clear();
        doorClosedState.clear();
    }
}
