package vn.hoidanit.jobhunter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SalaryTextTests {
    @Test
    void formatsRangesAndOpenEnds() {
        assertEquals("20 – 30 triệu đ", SalaryText.format(20_000_000, 30_000_000.0));
        assertEquals("25 triệu đ", SalaryText.format(25_000_000, 25_000_000.0));
        assertEquals("Từ 15 triệu đ", SalaryText.format(15_000_000, null));
        assertEquals("Tới 40 triệu đ", SalaryText.format(0, 40_000_000.0));
        assertEquals("Thỏa thuận", SalaryText.format(0, null));
        assertEquals("Thỏa thuận", SalaryText.format(0, 0.0));
        assertEquals("12,5 – 20 triệu đ", SalaryText.format(12_500_000, 20_000_000.0));
        assertEquals("500.000 – 900.000 đ", SalaryText.format(500_000, 900_000.0));
        assertEquals("1 – 1,5 tỷ đ", SalaryText.format(1_000_000_000, 1_500_000_000.0));
    }
}
