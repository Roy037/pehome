package vn.hoidanit.jobhunter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.hoidanit.jobhunter.repository.UserRepository;

@Configuration
public class PermissionInterceptorConfiguration implements WebMvcConfigurer {
    private final UserRepository userRepository;

    public PermissionInterceptorConfiguration(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    PermissionInterceptor getPermissionInterceptor() {
        return new PermissionInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] whiteList = {
                "/", "/error", "/api/v1/auth/**", "/storage/company/**", "/storage/avatar/**", "/api/v1/files",
                "/api/v1/me/**", "/api/v1/plans", "/api/v1/payments/**"
        };
        // runs first and for every API call, including the public list endpoints the permission check skips
        registry.addInterceptor(new FilterGuardInterceptor()).addPathPatterns("/api/v1/**");
        registry.addInterceptor(new LockedAccountInterceptor(this.userRepository)).addPathPatterns("/api/v1/**");
        registry.addInterceptor(getPermissionInterceptor())
                .excludePathPatterns(whiteList);
    }
}
