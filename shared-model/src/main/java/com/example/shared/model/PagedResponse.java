package com.example.shared.model;

import java.util.List;

/**
 * Generic paginated response wrapper for API endpoints.
 * Provides standard pagination metadata alongside content.
 *
 * @param <T> the type of elements in the page
 */
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    /**
     * Factory method to create a PagedResponse from content and pagination info.
     *
     * @param content the list of items for this page
     * @param page current page number (0-based)
     * @param size page size
     * @param totalElements total number of elements across all pages
     * @return a new PagedResponse instance
     */
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PagedResponse<>(content, page, size, totalElements, totalPages);
    }

    /**
     * Check if this is the first page.
     */
    public boolean isFirst() {
        return page == 0;
    }

    /**
     * Check if this is the last page.
     */
    public boolean isLast() {
        return page >= totalPages - 1;
    }

    /**
     * Check if there is a next page.
     */
    public boolean hasNext() {
        return page < totalPages - 1;
    }

    /**
     * Check if there is a previous page.
     */
    public boolean hasPrevious() {
        return page > 0;
    }
}
