package cl.helvoca.telephony.twilio;

import cl.helvoca.telephony.twilio.trial.TrialVoiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TwilioSignatureValidationFilter extends OncePerRequestFilter {
    private final TwilioSignatureValidator validator;
    private final TrialVoiceProperties trial;

    public TwilioSignatureValidationFilter(TwilioSignatureValidator validator,
                                           TrialVoiceProperties trial) {
        this.validator = validator;
        this.trial = trial;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/webhooks/v1/twilio/trial/")
                && trial.isEnabled()
                && trial.matchesWebhookSecret(request.getParameter("trialKey"))) {
            return true;
        }
        return !path.startsWith("/webhooks/v1/twilio/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!validator.validateHttp(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Invalid Twilio signature");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
