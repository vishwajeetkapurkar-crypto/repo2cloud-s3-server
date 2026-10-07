package com.repo2cloud.s3server.dto;

public class ByteRange {

    private final long start;
    private final long end;

    public ByteRange(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public long getLength() {
        return end - start + 1;
    }
}