package in.mjmasala.api.service;

import in.mjmasala.api.domain.Enquiry;
import in.mjmasala.api.domain.EnquiryKind;
import in.mjmasala.api.domain.EnquiryStatus;
import in.mjmasala.api.dto.enquiry.CreateEnquiryRequest;
import in.mjmasala.api.dto.enquiry.EnquiryDto;
import in.mjmasala.api.repository.EnquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Wholesale quote requests and contact-form messages. */
@Service
public class EnquiryService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final EnquiryRepository enquiries;

    public EnquiryService(EnquiryRepository enquiries) {
        this.enquiries = enquiries;
    }

    @Transactional
    public EnquiryDto create(CreateEnquiryRequest request) {
        Enquiry enquiry = new Enquiry();
        enquiry.setKind(request.kind() == null ? EnquiryKind.CONTACT : request.kind());
        enquiry.setStatus(EnquiryStatus.NEW);
        enquiry.setName(request.name().trim());
        enquiry.setEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
        enquiry.setPhone(request.phone());
        enquiry.setCompany(request.company());
        enquiry.setCity(request.city());
        enquiry.setMonthlyVolume(request.monthlyVolume());
        enquiry.setMessage(request.message());
        return toDto(enquiries.save(enquiry));
    }

    @Transactional(readOnly = true)
    public List<EnquiryDto> listAll() {
        return enquiries.findAllByOrderByIdDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public EnquiryDto updateStatus(Long id, EnquiryStatus status, String note) {
        Enquiry enquiry = enquiries.findById(id)
                .orElseThrow(() -> in.mjmasala.api.exception.NotFoundException.of("Enquiry", id));
        enquiry.setStatus(status);
        if (note != null) {
            enquiry.setInternalNote(note);
        }
        return toDto(enquiries.save(enquiry));
    }

    private EnquiryDto toDto(Enquiry e) {
        return new EnquiryDto(
                e.getId(),
                e.getKind().name(),
                e.getStatus().name(),
                e.getName(),
                e.getEmail(),
                e.getPhone(),
                e.getCompany(),
                e.getCity(),
                e.getMonthlyVolume(),
                e.getMessage(),
                e.getCreatedAt() != null ? ISO.format(e.getCreatedAt()) : null);
    }
}
