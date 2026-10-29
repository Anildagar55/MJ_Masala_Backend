package in.mjmasala.api.web;

import in.mjmasala.api.domain.EnquiryKind;
import in.mjmasala.api.dto.enquiry.CreateEnquiryRequest;
import in.mjmasala.api.dto.enquiry.EnquiryDto;
import in.mjmasala.api.service.EnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public forms. Both accept the same payload; the path decides the kind, so the
 * wholesale form cannot accidentally file itself as a general enquiry.
 */
@RestController
@Tag(name = "Enquiries", description = "Wholesale quote requests and contact messages")
public class EnquiryController {

    private final EnquiryService enquiries;

    public EnquiryController(EnquiryService enquiries) {
        this.enquiries = enquiries;
    }

    @PostMapping("/wholesale/enquiries")
    @Operation(summary = "Request a wholesale quote")
    public ResponseEntity<EnquiryDto> wholesale(@Valid @RequestBody CreateEnquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enquiries.create(withKind(request, EnquiryKind.WHOLESALE)));
    }

    @PostMapping("/contact/messages")
    @Operation(summary = "Send a message from the contact page")
    public ResponseEntity<EnquiryDto> contact(@Valid @RequestBody CreateEnquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enquiries.create(withKind(request, EnquiryKind.CONTACT)));
    }

    private CreateEnquiryRequest withKind(CreateEnquiryRequest r, EnquiryKind kind) {
        return new CreateEnquiryRequest(kind, r.name(), r.email(), r.phone(), r.company(),
                r.city(), r.monthlyVolume(), r.message());
    }
}
