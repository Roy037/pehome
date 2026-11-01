package vn.hoidanit.jobhunter.util.constant;

import lombok.Getter;

/**
 * What an employer can buy. The prices are placeholders to tune; `days` is how long one purchase lasts and `slots`
 * how many extra open jobs a job pack allows.
 */
@Getter
public enum EmployerProductEnum {
    JOB_PIN_7("Ghim tin 7 ngày", "ghim tin 7 ngay", 99_000, 7, 0),
    JOB_PIN_30("Ghim tin 30 ngày", "ghim tin 30 ngay", 299_000, 30, 0),
    JOB_SLOTS_5("Thêm 5 tin đang mở", "them 5 tin dang mo", 199_000, 30, 5),
    TALENT_30("Mở khóa kho ứng viên", "mo khoa kho ung vien", 499_000, 30, 0);

    /** Open jobs a company may have without buying anything. */
    public static final int FREE_OPEN_JOBS = 3;

    private final String label;
    /** What the payment page prints: VNPay refuses accents. */
    private final String asciiLabel;
    private final long priceVnd;
    private final int days;
    private final int slots;

    EmployerProductEnum(String label, String asciiLabel, long priceVnd, int days, int slots) {
        this.label = label;
        this.asciiLabel = asciiLabel;
        this.priceVnd = priceVnd;
        this.days = days;
        this.slots = slots;
    }

    public boolean isPin() {
        return this == JOB_PIN_7 || this == JOB_PIN_30;
    }
}
