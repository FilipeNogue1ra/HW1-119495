package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pt.ua.deti.tqs.tuttibox.core.model.Compartment;
import pt.ua.deti.tqs.tuttibox.core.model.CompartmentState;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.ports.out.LockerHardwarePort;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import java.util.*;

@Controller
public class OperatorUiController {

    private final LockerStationRepository stationRepo;
    private final CompartmentRepository compartmentRepo;
    private final LockerHardwarePort hardwarePort;

    public OperatorUiController(LockerStationRepository stationRepo, CompartmentRepository compartmentRepo, LockerHardwarePort hardwarePort) {
        this.stationRepo = stationRepo;
        this.compartmentRepo = compartmentRepo;
        this.hardwarePort = hardwarePort;
    }

    @GetMapping("/operator")
    public String operatorView(Model model) {
        List<LockerStation> stations = stationRepo.findAll();
        model.addAttribute("stations", stations);
        return "operator";
    }

    @GetMapping("/api/v1/operator/stations")
    @ResponseBody
    public List<Map<String, Object>> getStations() {
        List<LockerStation> stations = stationRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LockerStation s : stations) {
            List<Compartment> comps = compartmentRepo.findByStation(s);
            long freeCount = comps.stream().filter(c -> c.getState() == CompartmentState.FREE).count();
            Map<String, Object> map = new HashMap<>();
            map.put("stationCode", s.getStationCode());
            map.put("name", s.getName());
            map.put("address", s.getAddress());
            map.put("city", s.getCity());
            map.put("totalCompartments", comps.size());
            map.put("freeCompartments", freeCount);
            result.add(map);
        }
        return result;
    }

    @GetMapping("/api/v1/operator/stations/{stationCode}/compartments")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getStationCompartments(@PathVariable String stationCode) {
        Optional<LockerStation> optStation = stationRepo.findByStationCode(stationCode);
        if (optStation.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LockerStation station = optStation.get();
        List<Compartment> comps = compartmentRepo.findByStation(station);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Compartment c : comps) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("compartmentNumber", c.getCompartmentNumber());
            map.put("dimension", c.getDimension().name());
            map.put("state", c.getState().name());
            map.put("isDoorClosed", hardwarePort.isDoorClosed(stationCode, c.getCompartmentNumber()));
            map.put("maintenanceNote", c.getMaintenanceNote() != null ? c.getMaintenanceNote() : "");
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/v1/operator/stations/{stationCode}/compartments/{compNumber}/unlock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unlockDoor(@PathVariable String stationCode, @PathVariable String compNumber) {
        boolean ok = hardwarePort.unlockDoor(stationCode, compNumber);
        Map<String, Object> res = new HashMap<>();
        res.put("success", ok);
        res.put("doorClosed", hardwarePort.isDoorClosed(stationCode, compNumber));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/v1/operator/stations/{stationCode}/compartments/{compNumber}/close")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> closeDoor(@PathVariable String stationCode, @PathVariable String compNumber) {
        boolean ok = hardwarePort.closeDoor(stationCode, compNumber);
        Map<String, Object> res = new HashMap<>();
        res.put("success", ok);
        res.put("doorClosed", hardwarePort.isDoorClosed(stationCode, compNumber));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/v1/operator/stations/{stationCode}/compartments/{compNumber}/toggle-service")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleOutOfService(@PathVariable String stationCode, @PathVariable String compNumber) {
        Optional<LockerStation> optStation = stationRepo.findByStationCode(stationCode);
        if (optStation.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Optional<Compartment> optComp = compartmentRepo.findByStationAndCompartmentNumber(optStation.get(), compNumber);
        if (optComp.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Compartment comp = optComp.get();
        if (comp.getState() == CompartmentState.OUT_OF_SERVICE) {
            comp.setState(CompartmentState.FREE);
            comp.setMaintenanceNote(null);
            hardwarePort.resetDoor(stationCode, compNumber);
        } else {
            comp.setState(CompartmentState.OUT_OF_SERVICE);
            comp.setMaintenanceNote("Sinalizado pelo Operador via Painel de Controlo.");
        }
        compartmentRepo.save(comp);

        Map<String, Object> res = new HashMap<>();
        res.put("newState", comp.getState().name());
        return ResponseEntity.ok(res);
    }
}
