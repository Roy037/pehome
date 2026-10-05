package vn.hoidanit.jobhunter.service;

import lombok.extern.slf4j.Slf4j;
import vn.hoidanit.jobhunter.util.FileSignature;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import vn.hoidanit.jobhunter.util.error.StorageException;

@Slf4j
@Service
public class FileService {

    @Value("${hoidanit.upload-file.base-uri}")
    private String baseURI;

    public void createDirectory(String folder) throws URISyntaxException {
        URI uri = new URI(folder);
        Path path = Paths.get(uri);
        File tmpDir = new File(path.toString());
        if (!tmpDir.isDirectory()) {
            try {
                Files.createDirectories(tmpDir.toPath());
                log.info("Created upload directory {}", tmpDir.toPath());
            } catch (IOException e) {
                log.error("Cannot create upload directory {}", tmpDir.toPath(), e);
            }
        }

    }

    public String store(MultipartFile file, String folder, Long ownerId) throws URISyntaxException, IOException {
        // create unique filename
        // keep only URL-safe characters: names with spaces or accents would break the storage URI
        String safeName = file.getOriginalFilename() == null ? "file"
                : java.text.Normalizer.normalize(file.getOriginalFilename(), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                        .replaceAll("[^A-Za-z0-9._-]+", "-");
        String finalName = System.currentTimeMillis() + "-" + (ownerId == null ? "" : "u" + ownerId + "-") + safeName;

        URI uri = new URI(baseURI + folder + "/" + finalName);
        Path path = Paths.get(uri);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, path,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        return finalName;
    }

    public long getFileLength(String fileName, String folder) throws URISyntaxException {
        URI uri = new URI(baseURI + folder + "/" + fileName);
        Path path = Paths.get(uri);

        File tmpDir = new File(path.toString());

        // file không tồn tại, hoặc file là 1 director => return 0
        if (!tmpDir.exists() || tmpDir.isDirectory())
            return 0;
        return tmpDir.length();
    }

    public InputStreamResource getResource(String fileName, String folder)
            throws URISyntaxException, FileNotFoundException {
        URI uri = new URI(baseURI + folder + "/" + fileName);
        Path path = Paths.get(uri);

        File file = new File(path.toString());
        return new InputStreamResource(new FileInputStream(file));
    }

    /**
     * Streams a stored CV to the caller. The caller has already been authorised; this only validates the stored
     * name (no path tricks), picks the content type from the extension and keeps the response out of shared caches.
     */
    private static final java.util.Map<String, String> CV_TYPES = java.util.Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "odt", "application/vnd.oasis.opendocument.text",
            "rtf", "application/rtf",
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private static final java.util.regex.Pattern OWNED = java.util.regex.Pattern.compile("^\\d+-u(\\d+)-.+");

    /** True when `storedName` was uploaded by `userId` (names from before owner ids were added are not checked). */
    public static boolean isUploadedBy(String storedName, long userId) {
        java.util.regex.Matcher m = OWNED.matcher(storedName == null ? "" : storedName);
        return !m.matches() || Long.parseLong(m.group(1)) == userId;
    }

    /** The user id carried in a stored name (`<time>-u<id>-<name>`), or null for names without one. */
    public static Long uploaderOf(String storedName) {
        java.util.regex.Matcher m = OWNED.matcher(storedName == null ? "" : storedName);
        return m.matches() ? Long.parseLong(m.group(1)) : null;
    }

    public ResponseEntity<Resource> serveResume(String storedName) throws StorageException {
        return servePrivate("resume", storedName);
    }

    /** Streams a file of a private folder (the caller has already decided that this person may see it). */
    public ResponseEntity<Resource> servePrivate(String folder, String storedName) throws StorageException {
        if (storedName == null || !storedName.matches("[A-Za-z0-9._-]+") || storedName.contains("..")) {
            throw new StorageException("Tên tệp không hợp lệ.");
        }
        try {
            long length = getFileLength(storedName, folder);
            if (length == 0) {
                throw new ResourceNotFoundException("Không tìm thấy tệp (có thể đã bị xoá).");
            }
            String extension = FileSignature.extensionOf(storedName);
            MediaType type = MediaType.parseMediaType(CV_TYPES.getOrDefault(extension, "application/octet-stream"));
            // PDFs and images open in the page; Word, ODT and RTF files are always downloaded
            boolean inline = "pdf".equals(extension) || "image".equals(type.getType());
            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(storedName).build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .contentLength(length)
                    .contentType(type)
                    .body(getResource(storedName, folder));
        } catch (URISyntaxException | FileNotFoundException e) {
            throw new StorageException("Không đọc được tệp.");
        }
    }
}
