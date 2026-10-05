package vn.hoidanit.jobhunter.util.error;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import vn.hoidanit.jobhunter.domain.response.RestResponse;

@RestControllerAdvice
public class GlobalException {

    // 400: the request itself is wrong (bad input, refresh-token problems).
    @ExceptionHandler(value = {
            IdInvalidException.class
    })
    public ResponseEntity<RestResponse<Object>> handleIdException(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Exception occurs...", ex.getMessage());
    }

    // 401: wrong e-mail or password at login.
    @ExceptionHandler(value = {
            UsernameNotFoundException.class,
            BadCredentialsException.class
    })
    public ResponseEntity<RestResponse<Object>> handleBadCredentials(Exception ex) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    // 404: unknown URL, or a record that does not exist.
    @ExceptionHandler(value = {
            NoResourceFoundException.class,
    })
    public ResponseEntity<RestResponse<Object>> handleNotFoundException(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "404 Not Found. URL may not exist...", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<RestResponse<Object>> handleResourceNotFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    // 409: duplicate data, or data that other records still point at.
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<RestResponse<Object>> handleConflict(Exception ex) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // 503: a feature that needs outside credentials (the payment gateway) is not set up yet.
    @ExceptionHandler(GatewayNotConfiguredException.class)
    public ResponseEntity<RestResponse<Object>> handleGatewayNotConfigured(Exception ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage());
    }

    // 429: too many attempts (failed log-ins).
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<RestResponse<Object>> handleTooManyRequests(Exception ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestResponse<Object>> handleDataIntegrity(Exception ex) {
        return build(HttpStatus.CONFLICT, "Conflict",
                "Dữ liệu bị trùng hoặc đang có liên kết với dữ liệu khác trong hệ thống nên không thể thực hiện thao tác này.");
    }

    // Spring MVC's own client errors: answered here so they keep their real status (otherwise they fall through to the
    // /error dispatch, where the permission check turns a 400 into a 403 and an anonymous caller sees 401).
    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class })
    public ResponseEntity<RestResponse<Object>> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Dữ liệu gửi lên không hợp lệ.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestResponse<Object>> handleMethodNotAllowed(Exception ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "Phương thức không được hỗ trợ cho đường dẫn này.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestResponse<Object>> handleUnsupportedMediaType(Exception ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", "Định dạng dữ liệu không được hỗ trợ.");
    }

    private static ResponseEntity<RestResponse<Object>> build(HttpStatus status, String error, Object message) {
        RestResponse<Object> res = new RestResponse<Object>();
        res.setStatusCode(status.value());
        res.setError(error);
        res.setMessage(message);
        return ResponseEntity.status(status).body(res);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> validationError(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        final List<FieldError> fieldErrors = result.getFieldErrors();

        RestResponse<Object> res = new RestResponse<Object>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError(ex.getBody().getDetail());

        List<String> errors = fieldErrors.stream().map(f -> f.getDefaultMessage()).collect(Collectors.toList());
        res.setMessage(errors.size() > 1 ? errors : errors.get(0));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(value = {
            StorageException.class,
    })
    public ResponseEntity<RestResponse<Object>> handleFileUploadException(Exception ex) {
        RestResponse<Object> res = new RestResponse<Object>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setMessage(ex.getMessage());
        res.setError("Exception upload file...");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(value = {
            PermissionException.class,
    })
    public ResponseEntity<RestResponse<Object>> handlePermissionException(Exception ex) {
        RestResponse<Object> res = new RestResponse<Object>();
        res.setStatusCode(HttpStatus.FORBIDDEN.value());
        res.setError("Forbidden");
        res.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(res);
    }


    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RestResponse<Object>> handleUploadTooLarge(Exception ex) {
        RestResponse<Object> res = new RestResponse<Object>();
        res.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE.value());
        res.setError("Payload Too Large");
        res.setMessage("Tệp tải lên vượt quá 5MB.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(res);
    }
}
