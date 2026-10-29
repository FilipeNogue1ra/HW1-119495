package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.TerminalStatusResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import java.util.List;

@Controller
public class TerminalUiController {

    private final LockerStationRepository stationRepo;
    private final TerminalService terminalService;

    public TerminalUiController(LockerStationRepository stationRepo, TerminalService terminalService) {
        this.stationRepo = stationRepo;
        this.terminalService = terminalService;
    }

    @GetMapping("/terminal")
    public String terminalView(@RequestParam(required = false) String station, Model model) {
        List<LockerStation> stations = stationRepo.findAll();
        model.addAttribute("stations", stations);

        LockerStation currentStation = stations.stream()
                .filter(s -> s.getStationCode().equalsIgnoreCase(station))
                .findFirst()
                .orElse(stations.isEmpty() ? null : stations.get(0));

        model.addAttribute("selectedStation", currentStation);

        if (currentStation != null) {
            TerminalStatusResponse status = terminalService.getTerminalStatus(currentStation.getStationCode());
            model.addAttribute("terminalStatus", status);
        }

        return "terminal";
    }
}
