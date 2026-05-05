package com.imam.allobank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillGroupDTO {
    private Long id;
    private String name;
    private Set<ParticipantDTO> participants;
}
