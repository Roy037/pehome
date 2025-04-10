package vn.hoidanit.jobhunter.domain.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultPaginationDTO {
    private Meta meta;
    private Object result;

}
