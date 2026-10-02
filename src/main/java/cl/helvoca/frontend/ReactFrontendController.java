package cl.helvoca.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReactFrontendController {

    @GetMapping({"/app", "/app/", "/app/plan"})
    public String application() {
        return "forward:/app/index.html";
    }
}
