package cl.helvoca.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReactFrontendController {

    @GetMapping({"/app", "/app/", "/app/plan", "/app/inventory", "/app/inventory/", "/app/settings", "/app/settings/"})
    public String application() {
        return "forward:/app/index.html";
    }
}
