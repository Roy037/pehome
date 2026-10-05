package vn.hoidanit.jobhunter.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Human-readable salary for e-mails: "20 – 30 triệu đ", "Từ 15 triệu đ", "Tới 40 triệu đ", "Thỏa thuận". */
public final class SalaryText {
    private SalaryText() {
    }

    private static final NumberFormat NUMBER = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
    static {
        NUMBER.setMaximumFractionDigits(1);
    }

    private static String amount(double value) {
        if (value >= 1e9) {
            return NUMBER.format(value / 1e9) + " tỷ đ";
        }
        if (value >= 1e6) {
            return NUMBER.format(value / 1e6) + " triệu đ";
        }
        return NUMBER.format(value) + " đ";
    }

    // "20 – 30 triệu đ" rather than "20 triệu đ – 30 triệu đ" when both ends use the same unit
    private static String range(double min, double max) {
        String low = amount(min), high = amount(max);
        String lowUnit = low.substring(low.indexOf(' ') + 1), highUnit = high.substring(high.indexOf(' ') + 1);
        return lowUnit.equals(highUnit) ? low.substring(0, low.indexOf(' ')) + " – " + high : low + " – " + high;
    }

    public static String format(double min, Double max) {
        double top = max == null ? 0 : max;
        if (min <= 0 && top <= 0) {
            return "Thỏa thuận";
        }
        if (top <= 0) {
            return "Từ " + amount(min);
        }
        if (min <= 0) {
            return "Tới " + amount(top);
        }
        return top == min ? amount(min) : range(min, top);
    }
}
