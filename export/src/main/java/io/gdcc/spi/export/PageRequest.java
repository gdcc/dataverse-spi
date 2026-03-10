package io.gdcc.spi.export;

import java.util.Objects;

/**
 * Defines pagination parameters for data retrieval methods that return
 * potentially large collections of results.
 *
 * <p>Use {@link #unpaged()} for requests that should return all results in a single batch.</p>
 */
public final class PageRequest {
    
    private static final PageRequest UNPAGED = new PageRequest(0, Integer.MAX_VALUE);
    
    private final int offset;
    private final int limit;
    
    private PageRequest(int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("Offset must be >= 0, was: " + offset);
        if (limit < 1) throw new IllegalArgumentException("Limit must be >= 1, was: " + limit);
        this.offset = offset;
        this.limit = limit;
    }
    
    /**
     * Creates a page request with the given offset and limit.
     *
     * @param offset zero-based index of the first result to return
     * @param limit  maximum number of results to return
     * @return a new PageRequest
     */
    public static PageRequest of(int offset, int limit) {
        return new PageRequest(offset, limit);
    }
    
    /**
     * Returns a request for all results (no pagination).
     */
    public static PageRequest unpaged() {
        return UNPAGED;
    }
    
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    
    public boolean isPaged() { return !this.equals(UNPAGED); }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageRequest that = (PageRequest) o;
        return offset == that.offset && limit == that.limit;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(offset, limit);
    }
    
    @Override
    public String toString() {
        return isPaged()
            ? "PageRequest{offset=" + offset + ", limit=" + limit + "}"
            : "PageRequest{unpaged}";
    }
}
