package cl.helvoca.telephony.twilio;

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

    public TwilioSignatureValidationFilter(TwilioSignatureValidator validator) {
        this.validator = validator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/webhooks/v1/twilio/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!validator.validateHttp(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Twilio signature");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
