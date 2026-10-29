package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Enquiry;
import in.mjmasala.api.domain.EnquiryKind;
import in.mjmasala.api.domain.EnquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    List<Enquiry> findAllByOrderByIdDesc();

    List<Enquiry> findByStatusOrderByIdDesc(EnquiryStatus status);

    List<Enquiry> findByKindOrderByIdDesc(EnquiryKind kind);

    long countByStatus(EnquiryStatus status);
}
