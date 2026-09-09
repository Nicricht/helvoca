package cl.helvoca.business;
import jakarta.validation.Valid; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/business")
public class BusinessController { private final BusinessService service; public BusinessController(BusinessService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')") public BusinessResponse current(){return service.current();}
 @PatchMapping @PreAuthorize("hasRole('BUSINESS_ADMIN')") public BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest request){return service.update(request);} }
