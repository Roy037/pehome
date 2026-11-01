package vn.hoidanit.jobhunter.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.util.SalaryText;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    // Images go inline (cid:...) because Gmail shows neither SVG nor pictures from a localhost URL.
    private static final ClassPathResource LOGO = new ClassPathResource("mail/itjobs-logo.png");
    /** Key under which a job card carries its company logo file, for {@link #sendTemplate} to attach. */
    private static final String LOGO_FILE = "logoFile";

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // Behind an https address the logos are linked (what every mail app renders in the inbox); on plain http (local
    // development) nothing public exists to link to, so they travel inside the message instead.
    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl = "http://localhost:8080";

    @Value("${hoidanit.upload-file.base-uri}")
    private String uploadBaseUri;

    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;

    public EmailService(JavaMailSender javaMailSender, SpringTemplateEngine templateEngine) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
    }

    /** A notice rendered with templates/notice.html. Optional parts may be null; `ctaPath` is relative to the site. */
    public record Notice(String heading, String name, String intro, Map<String, String> details, String noteTitle,
            String note, String jobsTitle, List<Map<String, Object>> jobs, String ctaLabel, String ctaPath, String reason) {

        public static Notice of(String heading, String name, String intro, String ctaLabel, String ctaPath, String reason) {
            return new Notice(heading, name, intro, null, null, null, null, null, ctaLabel, ctaPath, reason);
        }

        public Notice details(Map<String, String> rows) {
            return new Notice(heading, name, intro, rows, noteTitle, note, jobsTitle, jobs, ctaLabel, ctaPath, reason);
        }

        public Notice note(String title, String text) {
            return new Notice(heading, name, intro, details, title, text, jobsTitle, jobs, ctaLabel, ctaPath, reason);
        }

        public Notice jobs(String title, List<Map<String, Object>> cards) {
            return new Notice(heading, name, intro, details, noteTitle, note, title, cards, ctaLabel, ctaPath, reason);
        }

        /** A button with no button at all (security / account notices that only inform). */
        public static Notice plain(String heading, String name, String intro, String reason) {
            return new Notice(heading, name, intro, null, null, null, null, null, null, null, reason);
        }
    }

    @Async
    public void sendNotice(String to, String subject, Notice notice) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", notice.heading());
        vars.put("name", notice.name() == null || notice.name().isBlank() ? "bạn" : notice.name());
        vars.put("intro", notice.intro());
        vars.put("details", notice.details() == null ? null : new LinkedHashMap<>(notice.details()));
        vars.put("noteTitle", notice.noteTitle());
        vars.put("note", notice.note());
        vars.put("jobsTitle", notice.jobsTitle());
        vars.put("jobs", notice.jobs());
        vars.put("ctaLabel", notice.ctaLabel());
        // a path on this site, or a full address (the interview meeting link)
        vars.put("ctaUrl", notice.ctaPath() == null ? null
                : notice.ctaPath().startsWith("http") ? notice.ctaPath() : this.frontendUrl + notice.ctaPath());
        vars.put("reason", notice.reason());
        render(to, subject, "notice", vars);
    }

    /** Renders templates/<templateName>.html with the variables and sends it off the request thread. */
    @Async
    public void sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        render(to, subject, templateName, variables);
    }

    private void render(String to, String subject, String templateName, Map<String, Object> variables) {
        Map<String, Object> all = new HashMap<>(variables);
        all.put("logoSrc", publicAssets() ? base() + "/mail/itjobs-logo.png" : "cid:logo");
        Context context = new Context();
        context.setVariables(all);
        Map<String, Resource> inline = new LinkedHashMap<>();
        inline.put("logo", LOGO);
        if (variables.get("jobs") instanceof List<?> jobs) {
            for (Object job : jobs) {
                if (job instanceof Map<?, ?> card && card.get(LOGO_FILE) instanceof Resource file
                        && card.get("logoSrc") instanceof String src && src.startsWith("cid:")) {
                    inline.putIfAbsent(src.substring(4), file);
                }
            }
        }
        // Mail apps (Gmail's "Unsubscribe" link) use these to offer one-click opt-out; without them bulk mail drifts into Spam.
        Map<String, String> headers = new LinkedHashMap<>();
        if (variables.get("unsubscribeApiUrl") instanceof String unsubscribeUrl) {
            headers.put("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }
        send(to, subject, this.templateEngine.process(templateName, context), inline, headers);
    }

    private void send(String to, String subject, String html, Map<String, Resource> inline, Map<String, String> headers) {
        MimeMessage mimeMessage = this.javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            if (!from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(plainText(html), html); // the text part is what mail filters and screen readers fall back on
            for (Map.Entry<String, Resource> image : inline.entrySet()) {
                if (html.contains("cid:" + image.getKey())) {
                    message.addInline(image.getKey(), image.getValue());
                }
            }
            for (Map.Entry<String, String> header : headers.entrySet()) {
                mimeMessage.setHeader(header.getKey(), header.getValue());
            }
            this.javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException e) {
            log.error("Could not send e-mail to {}: {}", to, e.toString());
        }
    }

    /** Job cards for an e-mail: title, company, salary, link, and the company logo (attached inline) or its initials. */
    public List<Map<String, Object>> jobCards(List<Job> jobs) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Job job : jobs) {
            Company company = job.getCompany();
            Map<String, Object> card = new HashMap<>();
            card.put("name", job.getName());
            card.put("company", company == null ? "" : company.getName());
            card.put("salary", SalaryText.format(job.getSalary(), job.getSalaryMax()));
            card.put("url", this.frontendUrl + "/job/" + job.getId());
            card.put("initials", initials(company == null ? null : company.getName()));
            Resource logo = company == null ? null : companyLogo(company.getLogo());
            card.put("logoSrc", logo == null ? null
                    : publicAssets() ? base() + "/storage/company/" + company.getLogo() : "cid:company-" + company.getId());
            card.put(LOGO_FILE, logo);
            cards.add(card);
        }
        return cards;
    }

    // The uploaded logo, if the file is really there (seeded rows can point at files this machine does not have).
    private Resource companyLogo(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(URI.create(this.uploadBaseUri + "company/")).resolve(Path.of(file).getFileName());
            return Files.isRegularFile(path) ? new FileSystemResource(path) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean publicAssets() {
        return this.backendUrl.startsWith("https://");
    }

    private String base() {
        return this.backendUrl.endsWith("/") ? this.backendUrl.substring(0, this.backendUrl.length() - 1) : this.backendUrl;
    }

    /** The message as plain text: links kept as "label (address)", tables and blocks turned into lines. */
    static String plainText(String html) {
        String text = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", "");
        text = text.replaceAll("(?is)<a\\s[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2 ($1)");
        text = text.replaceAll("(?i)<br\\s*/?>|</(tr|p|div|h1|h2|h3|li)>", "\n");
        text = text.replaceAll("(?s)<[^>]+>", " ");
        text = org.springframework.web.util.HtmlUtils.htmlUnescape(text);
        return text.replaceAll("[ \\t\\u00a0]+", " ").replaceAll("(?m)^ +| +$", "").replaceAll("\\n{2,}", "\n\n").trim();
    }

    static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "IT";
        }
        StringBuilder out = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (out.length() < 2) {
                out.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
            }
        }
        return out.toString();
    }
}
