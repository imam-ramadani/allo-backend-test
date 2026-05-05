package com.imam.allobank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBillGroupRequest {
    @NotBlank(message = "Bill group name cannot be blank")
    private String name;

    @NotEmpty(message = "At least one participant is required")
    private Set<CreateParticipantRequest> participants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateParticipantRequest {
        @NotBlank(message = "Participant name cannot be blank")
        private String name;

        @NotBlank(message = "Email cannot be blank")
        private String email;
    }
}
