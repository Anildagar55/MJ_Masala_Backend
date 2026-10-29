package in.mjmasala.api.dto.admin;

import in.mjmasala.api.domain.EnquiryStatus;
import jakarta.validation.constraints.NotNull;

public record EnquiryUpdateRequest(@NotNull EnquiryStatus status, String note) {
}
