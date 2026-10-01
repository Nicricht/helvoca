package cl.helvoca.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReactFrontendControllerTest {

    @Test
    void directReactRouteForwardsToTheBuiltApplication() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReactFrontendController()).build();

        mvc.perform(get("/app/plan"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
    }
}
