package com.icbc.aiops.langfuse.api;

import java.util.List;

@lombok.Value
public class PageResponse<T> {
    List<T> items;
    int page;
    int size;
    long total;
    int totalPages;
    boolean hasNext;

    public List<T> items() { return items; }
    public int page() { return page; }
    public int size() { return size; }
    public long total() { return total; }
    public int totalPages() { return totalPages; }
    public boolean hasNext() { return hasNext; }


    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int computedTotalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, computedTotalPages,
                page + 1 < computedTotalPages);
    }

    /** 数据源探测可以比再次执行完整 count 更低成本地判断是否存在下一页。 */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total, boolean hasNext) {
        int computedTotalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, computedTotalPages, hasNext);
    }
}
