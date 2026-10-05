package vn.hoidanit.jobhunter.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.service.CompanyService;
import vn.hoidanit.jobhunter.service.JobService;

class ShareControllerTests {
    private JobService jobs;
    private CompanyService companies;
    private ShareController controller;

    @BeforeEach
    void setUp() {
        this.jobs = mock(JobService.class);
        this.companies = mock(CompanyService.class);
        this.controller = new ShareController(this.jobs, this.companies);
        ReflectionTestUtils.setField(this.controller, "frontendUrl", "https://itjobs.vn/");
        ReflectionTestUtils.setField(this.controller, "backendUrl", "https://api.itjobs.vn");
    }

    private Job job(String name, String logo) {
        Company company = new Company();
        company.setName("FPT Software");
        company.setLogo(logo);
        Job job = new Job();
        job.setName(name);
        job.setLocation("HANOI");
        job.setSalary(20_000_000);
        job.setSalaryMax(30_000_000d);
        job.setCompany(company);
        when(this.jobs.fetchJobById(7L)).thenReturn(Optional.of(job));
        when(this.jobs.isVisible(job)).thenReturn(true);
        when(this.companies.isVisible(company)).thenReturn(true);
        return job;
    }

    @Test
    void aVisibleJobGetsItsOwnPreview() {
        job("QA Automation", "fpt.png");
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("share", this.controller.job(7L, model, new MockHttpServletResponse()));
        assertEquals("QA Automation", model.get("title"));
        assertEquals("https://itjobs.vn/job/7", model.get("target"));
        assertEquals("https://api.itjobs.vn/storage/company/fpt.png", model.get("image"));
        assertTrue(String.valueOf(model.get("description")).contains("FPT Software") && String.valueOf(model.get("description")).contains("Hà Nội"));
    }

    @Test
    void aCompanyWithoutALogoFallsBackToTheSiteLogo() {
        job("QA", null);
        ExtendedModelMap model = new ExtendedModelMap();
        this.controller.job(7L, model, new MockHttpServletResponse());
        assertEquals("https://api.itjobs.vn/mail/itjobs-logo.png", model.get("image"));
    }

    @Test
    void hiddenOrMissingPostsOnlyGetTheFrontDoor() {
        Job locked = job("Hidden", "x.png");
        when(this.jobs.isVisible(locked)).thenReturn(false);
        assertEquals("redirect:https://itjobs.vn/job", this.controller.job(7L, new ExtendedModelMap(), new MockHttpServletResponse()));
        assertEquals("redirect:https://itjobs.vn/job", this.controller.job(99L, new ExtendedModelMap(), new MockHttpServletResponse()));
        Job pending = job("Pending company", "x.png");
        when(this.companies.isVisible(pending.getCompany())).thenReturn(false);
        assertEquals("redirect:https://itjobs.vn/job", this.controller.job(7L, new ExtendedModelMap(), new MockHttpServletResponse()));
    }

    @Test
    void theTemplateEscapesWhatThePosterTyped() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        Context context = new Context();
        context.setVariable("title", "\"><script>alert(1)</script>");
        context.setVariable("description", "a & b");
        context.setVariable("image", "https://api.itjobs.vn/mail/itjobs-logo.png");
        context.setVariable("target", "https://itjobs.vn/job/7");
        String html = engine.process("share", context);
        assertFalse(html.contains("<script>alert(1)</script>"), "title must not break out of the attribute");
        assertTrue(html.contains("og:title") && html.contains("og:image") && html.contains("window.location.replace"));
        assertTrue(html.contains("https://itjobs.vn/job/7"));
    }

    @Test
    void companyDescriptionBecomesAShortPlainSentence() {
        assertEquals("Xin chào thế giới", ShareController.plain("<p>Xin&nbsp;chào <b>thế</b> giới</p>", 50));
        assertTrue(ShareController.plain("a".repeat(300), 100).length() <= 100);
    }
}
