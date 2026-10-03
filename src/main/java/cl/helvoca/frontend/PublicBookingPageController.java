package cl.helvoca.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicBookingPageController {

    @GetMapping({"/reservar", "/reservar/"})
    public String publicBookingPage() {
        return "forward:/reservar/index.html";
    }
}
