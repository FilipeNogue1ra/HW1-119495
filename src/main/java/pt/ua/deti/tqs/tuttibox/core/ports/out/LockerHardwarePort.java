package pt.ua.deti.tqs.tuttibox.core.ports.out;

/**
 * Outbound Port representing physical locker hardware interactions.
 * Decouples the TuttiBox domain logic from cyber-physical microcontroller hardware.
 */
public interface LockerHardwarePort {

    /**
     * Sends an electrical signal to unlock and spring-open the compartment door.
     *
     * @param stationCode the station identifier (e.g. "STATION-AVEIRO-01")
     * @param compartmentNumber the compartment number (e.g. "A01")
     * @return true if the door unlocked and opened successfully; false if jammed or hardware fault
     */
    boolean unlockDoor(String stationCode, String compartmentNumber);

    /**
     * Closes and locks the compartment door.
     *
     * @param stationCode the station identifier
     * @param compartmentNumber the compartment number
     * @return true if the door closed and locked successfully; false if obstructed or jammed
     */
    boolean closeDoor(String stationCode, String compartmentNumber);

    /**
     * Queries the magnetic door sensor to check whether the door is physically closed.
     *
     * @param stationCode the station identifier
     * @param compartmentNumber the compartment number
     * @return true if the door is shut; false if open
     */
    boolean isDoorClosed(String stationCode, String compartmentNumber);

    /**
     * Resets compartment hardware to normal operating state after maintenance.
     *
     * @param stationCode the station identifier
     * @param compartmentNumber the compartment number
     */
    void resetDoor(String stationCode, String compartmentNumber);
}
