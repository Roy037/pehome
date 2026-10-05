package vn.hoidanit.jobhunter.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Checks a file by its first bytes ("magic number"), so a renamed .exe or .html cannot pass as a PDF or an image. */
public final class FileSignature {

    private FileSignature() {
    }

    /** Extensions allowed in each upload folder. */
    public static final Map<String, List<String>> ALLOWED = Map.of(
            "resume", List.of("pdf", "doc", "docx", "odt", "rtf", "jpg", "jpeg", "png", "webp"),
            "company", List.of("jpg", "jpeg", "png", "webp"),
            "company-doc", List.of("pdf", "jpg", "jpeg", "png", "webp"),
            "avatar", List.of("jpg", "jpeg", "png", "webp"));

    public static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    public static boolean matches(String extension, byte[] head) {
        return switch (extension) {
            case "pdf" -> startsWith(head, 0, '%', 'P', 'D', 'F', '-');
            case "png" -> startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "webp" -> startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P');
            case "doc" -> startsWith(head, 0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            // docx and odt are zip containers: only the container is checked
            case "docx", "odt" -> startsWith(head, 0, 'P', 'K', 3, 4);
            case "rtf" -> startsWith(head, 0, '{', '\\', 'r', 't', 'f');
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    public static String describe(String folder) {
        return String.join(", ", ALLOWED.getOrDefault(folder, Arrays.asList()));
    }
}
