package com.payflow.account.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for paged results (Spring discourages serialising PageImpl directly). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
