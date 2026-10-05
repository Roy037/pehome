package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateTests {

    static String render(String template, Map<String, Object> vars) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        Context context = new Context();
        context.setVariables(vars);
        return engine.process(template, context);
    }

    static Map<String, Object> statusVars() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", "Cảm ơn bạn đã ứng tuyển");
        vars.put("name", "Nguyen");
        vars.put("intro", "Sau khi xem xét...");
        vars.put("jobName", "QA Automation Engineer");
        vars.put("company", "FPT Software");
        vars.put("jobUrl", "http://localhost:3000/job/7");
        vars.put("note", null);
        return vars;
    }

    @Test
    void everyTemplateShowsTheInlineLogo() {
        for (String template : List.of("application-status", "interview-invitation", "job-alert", "reset-password", "notice")) {
            Map<String, Object> vars = statusVars();
            vars.put("jobs", List.of());
            vars.put("when", "09:00 05/10/2026");
            vars.put("meetingLink", "https://meet.google.com/x");
            vars.put("reschedule", false);
            vars.put("moreUrl", "http://localhost:3000/job");
            vars.put("resetUrl", "http://localhost:3000/reset-password?token=x");
            assertTrue(render(template, vars).contains("src=\"cid:logo\""), template);
        }
    }

    @Test
    void theLogoSourceIsWhateverTheServiceChose() {
        Map<String, Object> vars = statusVars();
        vars.put("jobs", List.of());
        vars.put("logoSrc", "https://api.itjobs.vn/mail/itjobs-logo.png");
        String html = render("application-status", vars);
        assertTrue(html.contains("src=\"https://api.itjobs.vn/mail/itjobs-logo.png\"") && !html.contains("cid:logo"));
    }

    @Test
    void plainTextKeepsLinksAndDropsMarkup() {
        String text = EmailService.plainText("<html><head><style>p{color:red}</style></head><body>"
                + "<h1>Xin chào</h1><p>Mở <a href=\"https://itjobs.vn/job/7\">tin tuyển dụng</a> &amp; ứng tuyển.</p>"
                + "<table><tr><td>Gói</td><td>Premium</td></tr></table></body></html>");
        assertTrue(text.contains("Xin chào"));
        assertTrue(text.contains("tin tuyển dụng (https://itjobs.vn/job/7) & ứng tuyển."));
        assertTrue(text.contains("Gói Premium"));
        assertFalse(text.contains("<") || text.contains("color:red"));
    }

    @Test
    void rejectionSuggestsOtherJobsAndASearch() {
        Map<String, Object> vars = statusVars();
        Map<String, Object> withLogo = new HashMap<>(Map.of("name", "Tester Java", "company", "VNG", "salary", "20 - 30 triệu",
                "url", "http://localhost:3000/job/9", "initials", "V", "logoSrc", "cid:company-5"));
        Map<String, Object> withoutLogo = new HashMap<>(Map.of("name", "QA Mobile", "company", "FPT Software", "salary", "Thỏa thuận",
                "url", "http://localhost:3000/job/10", "initials", "FS"));
        withoutLogo.put("logoSrc", null);
        vars.put("jobs", List.of(withLogo, withoutLogo));
        vars.put("ctaLabel", "Khám phá việc làm khác");
        vars.put("ctaUrl", "http://localhost:3000/job?skills=1,2");
        String html = render("application-status", vars);
        assertTrue(html.contains("Có thể bạn sẽ quan tâm"));
        assertTrue(html.contains("Tester Java") && html.contains("http://localhost:3000/job/9"));
        assertTrue(html.contains("Khám phá việc làm khác") && html.contains("http://localhost:3000/job?skills=1,2"));
        assertTrue(html.contains("src=\"cid:company-5\""), "uploaded company logo goes inline");
        assertTrue(html.contains(">FS</div>"), "a company without a logo file shows its initials");
    }

    @Test
    void noticeShowsDetailsNoteAndButton() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", "Thanh toán thành công");
        vars.put("name", "Nam");
        vars.put("intro", "Cảm ơn bạn đã nâng cấp.");
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Gói", "Premium");
        details.put("Số tiền", "149.000đ");
        vars.put("details", details);
        vars.put("noteTitle", "Góp ý từ itjobs");
        vars.put("note", "Bổ sung giấy phép kinh doanh.");
        vars.put("ctaLabel", "Tìm việc ngay");
        vars.put("ctaUrl", "http://localhost:3000/job");
        vars.put("reason", "Bạn nhận được email này vì đã thanh toán.");
        String html = render("notice", vars);
        assertTrue(html.contains(">Gói</td>") && html.contains(">Premium</td>") && html.contains(">149.000đ</td>"));
        assertTrue(html.contains("Góp ý từ itjobs") && html.contains("Bổ sung giấy phép kinh doanh."));
        assertTrue(html.contains("Tìm việc ngay") && html.contains("http://localhost:3000/job"));
        assertFalse(render("notice", Map.of("heading", "x", "intro", "y", "reason", "z")).contains("Tìm việc ngay"));
    }

    @Test
    void formatsMoneyAndInitials() {
        assertEquals("149.000đ", NotificationService.money(149_000));
        assertEquals("1.490.000đ", NotificationService.money(1_490_000));
        assertEquals("FS", EmailService.initials("FPT Software"));
        assertEquals("V", EmailService.initials("VNG"));
        assertEquals("IT", EmailService.initials(" "));
    }

    @Test
    void theDigestOffersOneClickUnsubscribe() {
        Map<String, Object> vars = statusVars();
        vars.put("jobs", List.of());
        vars.put("moreUrl", "http://localhost:3000/job");
        assertFalse(render("job-alert", vars).contains("Hủy nhận bản tin"));
        vars.put("unsubscribeUrl", "http://localhost:3000/huy-nhan-tin?token=5.abc");
        String html = render("job-alert", vars);
        assertTrue(html.contains("Hủy nhận bản tin") && html.contains("huy-nhan-tin?token=5.abc"));
    }

    @Test
    void otherStatusesKeepTheJobButtonAndNoSuggestions() {
        String html = render("application-status", statusVars());
        assertFalse(html.contains("Có thể bạn sẽ quan tâm"));
        assertTrue(html.contains("Xem tin tuyển dụng") && html.contains("http://localhost:3000/job/7"));
    }
}
