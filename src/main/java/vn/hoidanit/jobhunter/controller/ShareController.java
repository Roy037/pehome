package vn.hoidanit.jobhunter.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletResponse;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.service.CompanyService;
import vn.hoidanit.jobhunter.service.JobService;
import vn.hoidanit.jobhunter.util.SalaryText;

/**
 * The address people paste into Facebook, LinkedIn, Zalo and so on. The site itself is a single-page app with one
 * static head, so link previews would show the same generic card for every page; this page carries the og: tags for one
 * job or company and sends browsers on to the real page. Posts a visitor may not see only get the site's front door.
 */
@Controller
public class ShareController {

    private static final Map<String, String> CITIES = Map.of("HANOI", "Hà Nội", "HOCHIMINH", "TP. Hồ Chí Minh",
            "DANANG", "Đà Nẵng", "OTHER", "Nhiều địa điểm");

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl = "http://localhost:3000";

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl = "http://localhost:8080";

    private final JobService jobService;
    private final CompanyService companyService;

    public ShareController(JobService jobService, CompanyService companyService) {
        this.jobService = jobService;
        this.companyService = companyService;
    }

    @GetMapping("/share/job/{id}")
    public String job(@PathVariable("id") long id, Model model, HttpServletResponse response) {
        Job job = this.jobService.fetchJobById(id).orElse(null);
        Company company = job == null ? null : job.getCompany();
        if (job == null || !this.jobService.isVisible(job) || (company != null && !this.companyService.isVisible(company))) {
            return "redirect:" + site("/job");
        }
        String where = CITIES.getOrDefault(job.getLocation(), "Việt Nam");
        String description = (company == null ? "" : company.getName() + " đang tuyển. ") + "Mức lương: "
                + SalaryText.format(job.getSalary(), job.getSalaryMax()) + ". Địa điểm: " + where + ".";
        return page(model, response, job.getName(), description, company, site("/job/" + id), base() + "/share/job/" + id);
    }

    @GetMapping("/share/company/{id}")
    public String company(@PathVariable("id") long id, Model model, HttpServletResponse response) {
        Company company = this.companyService.findById(id).orElse(null);
        if (company == null || !this.companyService.isVisible(company)) {
            return "redirect:" + site("/company");
        }
        String description = company.getDescription() == null ? "Xem hồ sơ công ty và các vị trí đang tuyển trên itjobs."
                : plain(company.getDescription(), 200);
        return page(model, response, company.getName(), description, company, site("/company/" + id),
                base() + "/share/company/" + id);
    }

    private String page(Model model, HttpServletResponse response, String title, String description, Company company,
            String target, String self) {
        String logo = company == null || company.getLogo() == null || company.getLogo().isBlank()
                ? base() + "/mail/itjobs-logo.png"
                : base() + "/storage/company/" + company.getLogo();
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("image", logo);
        model.addAttribute("target", target);
        // og:url and canonical name this page itself: a crawler that followed them to the site would only find its generic tags
        model.addAttribute("self", self);
        response.setHeader("Cache-Control", "public, max-age=300");
        return "share";
    }

    private String site(String path) {
        String root = this.frontendUrl.endsWith("/") ? this.frontendUrl.substring(0, this.frontendUrl.length() - 1)
                : this.frontendUrl;
        return root + path;
    }

    private String base() {
        return this.backendUrl.endsWith("/") ? this.backendUrl.substring(0, this.backendUrl.length() - 1) : this.backendUrl;
    }

    // The company description is rich text (HTML); a link preview wants a short plain sentence.
    static String plain(String html, int max) {
        String text = html.replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max - 1).trim() + "…";
    }
}
