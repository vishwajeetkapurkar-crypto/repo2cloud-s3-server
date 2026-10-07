package com.repo2cloud.s3server.util;

import com.repo2cloud.s3server.dto.ByteRange;
import com.repo2cloud.s3server.exception.InvalidRangeException;

public final class RangeUtil {

    private RangeUtil() {
    }

    public static ByteRange parseRange(
            String rangeHeader,
            long fileSize) {

        if (rangeHeader == null ||
                !rangeHeader.startsWith("bytes=")) {

            return null;
        }

        String rangeValue =
                rangeHeader.substring("bytes=".length());

        // We support one range only.
        if (rangeValue.contains(",")) {
            throw new InvalidRangeException(
                    "Multiple ranges are not supported"
            );
        }

        String[] parts =
                rangeValue.split("-", -1);

        if (parts.length != 2) {
            throw new InvalidRangeException(
                    "Invalid Range header"
            );
        }

        String startPart = parts[0].trim();
        String endPart = parts[1].trim();

        long start;
        long end;

        try {

            if (startPart.isEmpty()) {

                // Example: bytes=-100
                long suffixLength =
                        Long.parseLong(endPart);

                if (suffixLength <= 0) {
                    throw new InvalidRangeException(
                            "Invalid suffix range"
                    );
                }

                if (suffixLength > fileSize) {
                    suffixLength = fileSize;
                }

                start = fileSize - suffixLength;
                end = fileSize - 1;

            } else {

                start = Long.parseLong(startPart);

                if (start < 0 || start >= fileSize) {
                    throw new InvalidRangeException(
                            "Range start is outside the file"
                    );
                }

                if (endPart.isEmpty()) {

                    // Example: bytes=100-
                    end = fileSize - 1;

                } else {

                    end = Long.parseLong(endPart);

                    if (end < start) {
                        throw new InvalidRangeException(
                                "Range end is before range start"
                        );
                    }

                    if (end >= fileSize) {
                        end = fileSize - 1;
                    }
                }
            }

        } catch (NumberFormatException e) {

            throw new InvalidRangeException(
                    "Invalid Range header"
            );
        }

        return new ByteRange(start, end);
    }
}