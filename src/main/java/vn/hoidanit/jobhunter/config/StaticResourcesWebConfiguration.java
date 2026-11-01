package vn.hoidanit.jobhunter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourcesWebConfiguration
        implements WebMvcConfigurer {

    @Value("${hoidanit.upload-file.base-uri}")
    private String baseURI;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Public art only. The "resume" folder is deliberately not mapped: CVs are streamed after an access check.
        registry.addResourceHandler("/storage/company/**").addResourceLocations(baseURI + "company/");
        registry.addResourceHandler("/storage/avatar/**").addResourceLocations(baseURI + "avatar/");
        // The itjobs logo used by e-mails when BACKEND_URL is public (https): linked, not attached.
        registry.addResourceHandler("/mail/**").addResourceLocations("classpath:/mail/");
    }
}
