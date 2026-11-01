package vn.hoidanit.jobhunter.domain.response.review;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;

@Getter
@Setter
public class ResCompanyReviewsDTO {
    private double average;
    private long total;
    // Keyed by star value 1..5; every key is always present.
    private Map<Integer, Long> distribution;
    private ResultPaginationDTO.Meta meta;
    private List<ResReviewDTO> result;
}
