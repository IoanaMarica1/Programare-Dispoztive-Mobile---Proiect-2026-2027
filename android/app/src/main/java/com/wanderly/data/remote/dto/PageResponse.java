package com.wanderly.data.remote.dto;

import java.util.List;

public class PageResponse<T> {
    public List<T> content;
    public int page;
    public int size;
    public long totalElements;
    public int totalPages;

    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
