package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.hardware;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryHardwareFakeTest {

    private InMemoryHardwareFake hardwareFake;
    private final String station = "STATION-AVEIRO-01";
    private final String compartment = "A01";

    @BeforeEach
    void setUp() {
        hardwareFake = new InMemoryHardwareFake();
    }

    @Test
    @DisplayName("Estado Inicial: qualquer porta começa fechada por defeito")
    void testInitialStateIsClosed() {
        assertTrue(hardwareFake.isDoorClosed(station, compartment), "A porta deve começar fechada.");
    }

    @Test
    @DisplayName("Caminho Feliz: unlockDoor destranca com sucesso e a porta passa a aberta")
    void testUnlockDoorSuccess() {
        boolean result = hardwareFake.unlockDoor(station, compartment);

        assertTrue(result, "O destrancamento deve ser bem sucedido.");
        assertFalse(hardwareFake.isDoorClosed(station, compartment), "O sensor deve indicar que a porta agora está aberta.");
    }

    @Test
    @DisplayName("Caminho Feliz: closeDoor fecha a porta e o sensor deteta fecho")
    void testCloseDoorSuccess() {
        hardwareFake.unlockDoor(station, compartment);
        assertFalse(hardwareFake.isDoorClosed(station, compartment));

        boolean closed = hardwareFake.closeDoor(station, compartment);

        assertTrue(closed, "A porta deve fechar com sucesso.");
        assertTrue(hardwareFake.isDoorClosed(station, compartment), "O sensor deve indicar que a porta voltou a estar fechada.");
    }

    @Test
    @DisplayName("Injeção de Falha (Guia P36): simulateDoorJam faz o destrancamento falhar")
    void testSimulatedDoorJamFailure() {
        hardwareFake.simulateDoorJam(station, compartment);
        assertTrue(hardwareFake.isDoorJammed(station, compartment), "A porta deve estar sinalizada como encravada.");

        boolean result = hardwareFake.unlockDoor(station, compartment);

        assertFalse(result, "O destrancamento deve falhar porque a tranca está encravada.");
        assertTrue(hardwareFake.isDoorClosed(station, compartment), "A porta não chegou a abrir fisicamente.");
    }

    @Test
    @DisplayName("Reset: resetDoor limpa avaria e repõe funcionamento normal")
    void testResetDoorClearsFailure() {
        hardwareFake.simulateDoorJam(station, compartment);
        assertFalse(hardwareFake.unlockDoor(station, compartment));

        hardwareFake.resetDoor(station, compartment);

        assertFalse(hardwareFake.isDoorJammed(station, compartment), "O encravamento deve ter sido removido.");
        assertTrue(hardwareFake.unlockDoor(station, compartment), "Após o reset, a porta deve voltar a abrir normalmente.");
    }
}
