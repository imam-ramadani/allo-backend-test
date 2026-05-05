package com.imam.allobank.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "split_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitTypeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer strategy; // 1=EQUAL,2=PERCENTAGE,3=EXACT_AMOUNT

    @Column(nullable = false)
    private String description; // textual name
}
