package vn.hoidanit.jobhunter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FileSignatureTests {
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    void acceptsTheRealFormats() {
        assertTrue(FileSignature.matches("pdf", "%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(FileSignature.matches("png", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)));
        assertTrue(FileSignature.matches("jpg", bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertTrue(FileSignature.matches("jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE1)));
        assertTrue(FileSignature.matches("webp", bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
    }

    @Test
    void acceptsWordOpenDocumentAndRtfFiles() {
        assertTrue(FileSignature.matches("doc", bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0, 0)));
        assertTrue(FileSignature.matches("docx", bytes('P', 'K', 3, 4, 20, 0)));
        assertTrue(FileSignature.matches("odt", bytes('P', 'K', 3, 4, 20, 0)));
        assertTrue(FileSignature.matches("rtf", "{\\rtf1\\ansi".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(FileSignature.matches("doc", bytes('P', 'K', 3, 4, 0, 0, 0, 0)), "a zip named .doc");
        assertFalse(FileSignature.matches("docx", bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)), "an old .doc named .docx");
        assertFalse(FileSignature.matches("rtf", "<html>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void refusesRenamedOrTruncatedFiles() {
        assertFalse(FileSignature.matches("pdf", "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(FileSignature.matches("pdf", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)), "a PNG named .pdf");
        assertFalse(FileSignature.matches("png", bytes('M', 'Z', 0, 0)), "an .exe named .png");
        assertFalse(FileSignature.matches("webp", bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' ')), "RIFF but not WEBP");
        assertFalse(FileSignature.matches("pdf", bytes('%', 'P')), "too short");
        assertFalse(FileSignature.matches("exe", bytes('M', 'Z', 0, 0)), "formats that are not allowed at all");
        assertFalse(FileSignature.matches("html", "<html>".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(FileSignature.matches("svg", "<svg>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void foldersAllowOnlyTheirOwnFormats() {
        assertEquals(java.util.List.of("pdf", "doc", "docx", "odt", "rtf", "jpg", "jpeg", "png", "webp"), FileSignature.ALLOWED.get("resume"));
        assertFalse(FileSignature.ALLOWED.get("resume").contains("svg"), "svg can carry scripts");
        assertFalse(FileSignature.ALLOWED.get("avatar").contains("docx"));
        assertFalse(FileSignature.ALLOWED.get("company").contains("pdf"));
        assertEquals("pdf", FileSignature.extensionOf("CV.Final.PDF"));
        assertEquals("", FileSignature.extensionOf("no-extension"));
        assertEquals("", FileSignature.extensionOf(null));
    }
}
