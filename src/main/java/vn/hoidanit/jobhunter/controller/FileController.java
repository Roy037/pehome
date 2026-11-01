package vn.hoidanit.jobhunter.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.hoidanit.jobhunter.domain.response.file.ResUploadFileDTO;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.service.FileService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.FileSignature;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.StorageException;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    @Value("${hoidanit.upload-file.base-uri}")
    private String baseURI;

    private static final List<String> FOLDERS = Arrays.asList("resume", "company", "company-doc", "avatar");
    private static final List<String> PUBLIC_FOLDERS = Arrays.asList("company", "avatar");
    // CVs and company documents carry the uploader's id and are streamed only after an access check
    private static final List<String> PRIVATE_FOLDERS = Arrays.asList("resume", "company-doc");

    private final FileService fileService;

    private static void checkFolder(String folder) throws StorageException {
        if (folder == null || !FOLDERS.contains(folder)) {
            throw new StorageException("Invalid folder. only allows " + FOLDERS);
        }
    }

    private final UserService userService;

    public FileController(FileService fileService, UserService userService) {
        this.fileService = fileService;
        this.userService = userService;
    }

    @PostMapping("/files")
    @ApiMessage("Upload single file")
    public ResponseEntity<ResUploadFileDTO> upload(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam("folder") String folder

    ) throws URISyntaxException, IOException, StorageException, IdInvalidException, PermissionException {
        checkFolder(folder);
        if (file == null || file.isEmpty()) {
            throw new StorageException("File is empty. Please upload a file.");
        }
        // getOriginalFilename get origin name
        String fileName = file.getOriginalFilename();
        String extension = FileSignature.extensionOf(fileName);
        if (!FileSignature.ALLOWED.get(folder).contains(extension)) {
            throw new StorageException("Chỉ chấp nhận tệp " + FileSignature.describe(folder).toUpperCase() + " cho thư mục này.");
        }
        // the first bytes must really be that format (a renamed .html or .exe is refused)
        if (!FileSignature.matches(extension, file.getInputStream().readNBytes(16))) {
            throw new StorageException("Nội dung tệp không phải " + extension.toUpperCase() + " hợp lệ.");
        }
        User me = this.userService.handleGetCurrentUser();
        // company logos and banners are public images: only employers (and admins) may publish them
        boolean admin = me.getRole() != null && "SUPER_ADMIN".equals(me.getRole().getName());
        if (("company".equals(folder) || "company-doc".equals(folder)) && me.getCompany() == null && !admin) {
            throw new PermissionException("Chỉ nhà tuyển dụng hoặc quản trị viên mới được tải tệp của công ty.");
        }
        // create a directory if not exist
        this.fileService.createDirectory(baseURI + folder);

        // store file; CVs carry the uploader's id so nobody else can attach (and later read) them
        String uploadFile = this.fileService.store(file, folder, PRIVATE_FOLDERS.contains(folder) ? me.getId() : null);

        ResUploadFileDTO res = new ResUploadFileDTO(uploadFile, Instant.now());

        return ResponseEntity.ok().body(res);
    }

    @GetMapping("/files")
    @ApiMessage("Download a file")
    public ResponseEntity<Resource> download(
            @RequestParam(name = "fileName", required = false) String fileName,
            @RequestParam(name = "folder", required = false) String folder)
            throws StorageException, URISyntaxException, FileNotFoundException {
        if (fileName == null || folder == null) {
            throw new StorageException("Missing required params : (fileName or folder) in query params.");
        }
        checkFolder(folder);
        if (!PUBLIC_FOLDERS.contains(folder)) {
            // CVs are only available through GET /resumes/{id}/document and licences through GET /companies/{id}/license,
            // which check who is asking.
            throw new StorageException("Invalid folder. only allows " + PUBLIC_FOLDERS);
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new StorageException("Invalid file name.");
        }

        // check file exist (and not a directory)
        long fileLength = this.fileService.getFileLength(fileName, folder);
        if (fileLength == 0) {
            throw new StorageException("File with name = " + fileName + " not found.");
        }

        // download a file
        InputStreamResource resource = this.fileService.getResource(fileName, folder);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentLength(fileLength)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
