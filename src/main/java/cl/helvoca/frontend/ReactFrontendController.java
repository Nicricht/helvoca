package cl.helvoca.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReactFrontendController {

    @GetMapping({
            "/app",
            "/app/",
            "/app/plan",
            "/app/inventory",
            "/app/inventory/",
            "/app/agenda",
            "/app/agenda/",
            "/app/orders",
            "/app/orders/",
            "/app/settings",
            "/app/settings/",
            "/app/settings/import",
            "/app/settings/import/",
            "/app/simulator",
            "/app/simulator/",
            "/app/internal/operations",
            "/app/internal/operations/"
    })
    public String application() {
        return "forward:/app/index.html";
    }
}
