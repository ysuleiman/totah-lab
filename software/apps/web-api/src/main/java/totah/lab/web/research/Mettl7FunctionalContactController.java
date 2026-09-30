package totah.lab.web.research;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public final class Mettl7FunctionalContactController {
    private final Mettl7FunctionalContactService service;

    public Mettl7FunctionalContactController(
            Mettl7FunctionalContactService service) {
        this.service = service;
    }

    @GetMapping("/mettl7-functional-contacts")
    public Mettl7FunctionalContactService.ReportView report() {
        return service.report();
    }
}
