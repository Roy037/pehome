package vn.hoidanit.jobhunter.util.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GenderEnum {
    // The database column stores the misspelled name; the API speaks "FEMALE".
    @JsonProperty("FEMALE")
    FEMAIE, MALE, OTHER;
}
