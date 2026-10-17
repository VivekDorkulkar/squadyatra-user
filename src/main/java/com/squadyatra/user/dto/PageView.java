package com.squadyatra.user.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
