package cl.helvoca.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicBookingPageControllerTest {

    @Test
    void publicBookingRouteForwardsToTheBuiltPublicApplication() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicBookingPageController()).build();

        mvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/reservar/index.html"));

        mvc.perform(get("/reservar/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/reservar/index.html"));
    }
}
