package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqReviewDTO {
    @Min(value = 1, message = "Điểm đánh giá từ 1 đến 5")
    @Max(value = 5, message = "Điểm đánh giá từ 1 đến 5")
    private int rating;

    @NotBlank(message = "Nội dung đánh giá không được để trống")
    @Size(max = 1000, message = "Nội dung đánh giá tối đa 1000 ký tự")
    private String content;
}
