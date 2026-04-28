package com.danzzan.infra.s3;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * S3 객체 key(prefix) 규칙을 한 곳에서 관리하는 헬퍼.
 * - ads/{uuid}-{fileName}
 * - notices/{yyyyMMdd}/{uuid}-{fileName}
 * - booths/{yyyyMMdd}/{uuid}-{fileName}
 * - profiles/{uuid}-{fileName}
 */
@Component
public class S3PathGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    public String generateAdImageKey(String originalFilename) {
        String safeName = sanitizeFileName(originalFilename);
        return "ads/" + UUID.randomUUID() + "-" + safeName;
    }

    public String generateNoticeImageKey(String originalFilename) {
        String safeName = sanitizeFileName(originalFilename);
        String date = LocalDate.now().format(DATE_FORMAT);
        return "notices/" + date + "/" + UUID.randomUUID() + "-" + safeName;
    }

    public String generateBoothImageKey(String originalFilename) {
        String safeName = sanitizeFileName(originalFilename);
        String date = LocalDate.now().format(DATE_FORMAT);
        return "booths/" + date + "/" + UUID.randomUUID() + "-" + safeName;
    }

    public String generateProfileImageKey(String originalFilename) {
        String safeName = sanitizeFileName(originalFilename);
        return "profiles/" + UUID.randomUUID() + "-" + safeName;
    }

    public String generatePubImageKey(String collegeName, String department, String originalFilename) {
        String safeCollegeName = sanitizePathSegment(collegeName);
        String safeDepartment = sanitizePathSegment(department);
        String safeName = sanitizeFileName(originalFilename);
        return "pub-images/" + safeCollegeName + "/" + safeDepartment + "/" + UUID.randomUUID() + "-" + safeName;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sanitizePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value
                .trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ");
    }
}

