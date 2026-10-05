package vn.hoidanit.jobhunter.config;

import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.PermissionRepository;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.constant.GenderEnum;

@Slf4j
@Service
public class DatabaseInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Seeding database (roles, permissions, admin user)...");
        long countPermissions = this.permissionRepository.count();
        long countRoles = this.roleRepository.count();
        long countUsers = this.userRepository.count();

        if (countPermissions == 0) {
            ArrayList<Permission> arr = new ArrayList<>();
            arr.add(new Permission("Create a company", "/api/v1/companies", "POST", "COMPANIES"));
            arr.add(new Permission("Update a company", "/api/v1/companies", "PUT", "COMPANIES"));
            arr.add(new Permission("Delete a company", "/api/v1/companies/{id}", "DELETE", "COMPANIES"));
            arr.add(new Permission("Get a company by id", "/api/v1/companies/{id}", "GET", "COMPANIES"));
            arr.add(new Permission("Get companies with pagination", "/api/v1/companies", "GET", "COMPANIES"));

            arr.add(new Permission("Create a job", "/api/v1/jobs", "POST", "JOBS"));
            arr.add(new Permission("Update a job", "/api/v1/jobs", "PUT", "JOBS"));
            arr.add(new Permission("Delete a job", "/api/v1/jobs/{id}", "DELETE", "JOBS"));
            arr.add(new Permission("Get a job by id", "/api/v1/jobs/{id}", "GET", "JOBS"));
            arr.add(new Permission("Get jobs with pagination", "/api/v1/jobs", "GET", "JOBS"));

            arr.add(new Permission("Create a permission", "/api/v1/permissions", "POST", "PERMISSIONS"));
            arr.add(new Permission("Update a permission", "/api/v1/permissions", "PUT", "PERMISSIONS"));
            arr.add(new Permission("Delete a permission", "/api/v1/permissions/{id}", "DELETE", "PERMISSIONS"));
            arr.add(new Permission("Get a permission by id", "/api/v1/permissions/{id}", "GET", "PERMISSIONS"));
            arr.add(new Permission("Get permissions with pagination", "/api/v1/permissions", "GET", "PERMISSIONS"));

            arr.add(new Permission("Create a resume", "/api/v1/resumes", "POST", "RESUMES"));
            arr.add(new Permission("Update a resume", "/api/v1/resumes", "PUT", "RESUMES"));
            arr.add(new Permission("Delete a resume", "/api/v1/resumes/{id}", "DELETE", "RESUMES"));
            arr.add(new Permission("Get a resume by id", "/api/v1/resumes/{id}", "GET", "RESUMES"));
            arr.add(new Permission("Get resumes with pagination", "/api/v1/resumes", "GET", "RESUMES"));

            arr.add(new Permission("Create a role", "/api/v1/roles", "POST", "ROLES"));
            arr.add(new Permission("Update a role", "/api/v1/roles", "PUT", "ROLES"));
            arr.add(new Permission("Delete a role", "/api/v1/roles/{id}", "DELETE", "ROLES"));
            arr.add(new Permission("Get a role by id", "/api/v1/roles/{id}", "GET", "ROLES"));
            arr.add(new Permission("Get roles with pagination", "/api/v1/roles", "GET", "ROLES"));

            arr.add(new Permission("Create a user", "/api/v1/users", "POST", "USERS"));
            arr.add(new Permission("Update a user", "/api/v1/users", "PUT", "USERS"));
            arr.add(new Permission("Delete a user", "/api/v1/users/{id}", "DELETE", "USERS"));
            arr.add(new Permission("Get a user by id", "/api/v1/users/{id}", "GET", "USERS"));
            arr.add(new Permission("Get users with pagination", "/api/v1/users", "GET", "USERS"));

            arr.add(new Permission("Create a subscriber", "/api/v1/subscribers", "POST", "SUBSCRIBERS"));
            arr.add(new Permission("Update a subscriber", "/api/v1/subscribers", "PUT", "SUBSCRIBERS"));
            arr.add(new Permission("Delete a subscriber", "/api/v1/subscribers/{id}", "DELETE", "SUBSCRIBERS"));
            arr.add(new Permission("Get a subscriber by id", "/api/v1/subscribers/{id}", "GET", "SUBSCRIBERS"));
            arr.add(new Permission("Get subscribers with pagination", "/api/v1/subscribers", "GET", "SUBSCRIBERS"));

            arr.add(new Permission("Download a file", "/api/v1/files", "POST", "FILES"));
            arr.add(new Permission("Upload a file", "/api/v1/files", "GET", "FILES"));

            this.permissionRepository.saveAll(arr);
        }

        if (countRoles == 0) {
            List<Permission> allPermissions = this.permissionRepository.findAll();

            Role adminRole = new Role();
            adminRole.setName("SUPER_ADMIN");
            adminRole.setDescription("Admin thì full permissions");
            adminRole.setActive(true);
            adminRole.setPermissions(allPermissions);

            this.roleRepository.save(adminRole);
        }

        // Existing databases skip the seed above, so later permissions are added and granted here.
        Role superAdmin = this.roleRepository.findByName("SUPER_ADMIN");
        for (Permission permission : List.of(
                new Permission("Get reviews with pagination", "/api/v1/reviews", "GET", "REVIEWS"),
                new Permission("Delete a review", "/api/v1/reviews/{id}", "DELETE", "REVIEWS"),
                new Permission("Create a review", "/api/v1/reviews", "POST", "REVIEWS"),
                new Permission("Update a review", "/api/v1/reviews", "PUT", "REVIEWS"),
                new Permission("Get saved jobs with pagination", "/api/v1/saved-jobs", "GET", "SAVED_JOBS"),
                new Permission("Create a saved job", "/api/v1/saved-jobs", "POST", "SAVED_JOBS"),
                new Permission("Delete a saved job", "/api/v1/saved-jobs/{id}", "DELETE", "SAVED_JOBS"),
                new Permission("Update a subscriber by id", "/api/v1/subscribers/{id}", "PUT", "SUBSCRIBERS"),
                new Permission("Approve a company", "/api/v1/companies/{id}/approve", "PUT", "COMPANIES"),
                // Skills used to bypass the interceptor; now that only GET is public, admins need real permissions.
                new Permission("Get skills with pagination", "/api/v1/skills", "GET", "SKILLS"),
                new Permission("Create a skill", "/api/v1/skills", "POST", "SKILLS"),
                new Permission("Update a skill", "/api/v1/skills", "PUT", "SKILLS"),
                new Permission("Delete a skill", "/api/v1/skills/{id}", "DELETE", "SKILLS"),
                new Permission("Change an application's status", "/api/v1/resumes/{id}/status", "PUT", "RESUMES"),
                new Permission("Evaluate an application", "/api/v1/resumes/{id}/evaluation", "PUT", "RESUMES"),
                new Permission("Send the job alert digest", "/api/v1/subscribers/send-digest", "POST", "SUBSCRIBERS"),
                new Permission("Get job reports with pagination", "/api/v1/job-reports", "GET", "JOB_REPORTS"),
                new Permission("Get orders with pagination", "/api/v1/orders", "GET", "ORDERS"),
                new Permission("Get platform statistics", "/api/v1/admin/stats", "GET", "STATS"),
                new Permission("Lock a job post", "/api/v1/jobs/{id}/lock", "PUT", "JOBS"),
                new Permission("Unlock a job post", "/api/v1/jobs/{id}/unlock", "PUT", "JOBS"),
                new Permission("Search the talent directory", "/api/v1/talents", "GET", "TALENTS"),
                new Permission("Fetch a candidate from the talent directory", "/api/v1/talents/{id}", "GET", "TALENTS"),
                new Permission("Stream a candidate CV from the talent directory", "/api/v1/talents/{id}/cv", "GET", "TALENTS"),
                new Permission("Reject a company", "/api/v1/companies/{id}/reject", "PUT", "COMPANIES"),
                new Permission("Lock a user account", "/api/v1/users/{id}/lock", "PUT", "USERS"),
                new Permission("Unlock a user account", "/api/v1/users/{id}/unlock", "PUT", "USERS"),
                new Permission("Dismiss a job report", "/api/v1/job-reports/{id}", "DELETE", "JOB_REPORTS"))) {
            if (!this.permissionRepository.existsByModuleAndApiPathAndMethod(
                    permission.getModule(), permission.getApiPath(), permission.getMethod())) {
                Permission saved = this.permissionRepository.save(permission);
                if (superAdmin != null) {
                    superAdmin.getPermissions().add(saved);
                    this.roleRepository.save(superAdmin);
                }
            }
        }

        // Employers (HR) manage their own company's jobs and applications; candidates need no permissions.
        seedRole("HR", "Nhà tuyển dụng", List.of(
                new String[] { "JOBS", "/api/v1/jobs", "GET" }, new String[] { "JOBS", "/api/v1/jobs/{id}", "GET" },
                new String[] { "JOBS", "/api/v1/jobs", "POST" }, new String[] { "JOBS", "/api/v1/jobs", "PUT" },
                new String[] { "JOBS", "/api/v1/jobs/{id}", "DELETE" },
                new String[] { "RESUMES", "/api/v1/resumes", "GET" }, new String[] { "RESUMES", "/api/v1/resumes/{id}", "GET" },
                new String[] { "RESUMES", "/api/v1/resumes", "PUT" },
                new String[] { "RESUMES", "/api/v1/resumes/{id}/status", "PUT" },
                new String[] { "RESUMES", "/api/v1/resumes/{id}/evaluation", "PUT" },
                new String[] { "COMPANIES", "/api/v1/companies", "GET" }, new String[] { "COMPANIES", "/api/v1/companies", "PUT" }));
        // A role that already exists keeps its permissions, so rights added later are granted here (idempotent).
        grantIfMissing("HR", List.of(
                new String[] { "RESUMES", "/api/v1/resumes/{id}/status", "PUT" },
                new String[] { "RESUMES", "/api/v1/resumes/{id}/evaluation", "PUT" },
                new String[] { "TALENTS", "/api/v1/talents", "GET" }, new String[] { "TALENTS", "/api/v1/talents/{id}", "GET" },
                new String[] { "TALENTS", "/api/v1/talents/{id}/cv", "GET" }));
        Role candidate = seedRole("NORMAL_USER", "Ứng viên", List.of());
        if (candidate != null) {
            for (User user : this.userRepository.findByRoleIsNull()) {
                user.setRole(candidate);
                this.userRepository.save(user);
            }
        }

        if (countUsers == 0) {
            User adminUser = new User();
            adminUser.setEmail("admin@gmail.com");
            adminUser.setAddress("hn");
            adminUser.setAge(25);
            adminUser.setGender(GenderEnum.MALE);
            adminUser.setName("I'm super admin");
            adminUser.setPassword(this.passwordEncoder.encode("123456"));

            Role adminRole = this.roleRepository.findByName("SUPER_ADMIN");
            if (adminRole != null) {
                adminUser.setRole(adminRole);
            }

            this.userRepository.save(adminUser);
        }

        if (countPermissions > 0 && countRoles > 0 && countUsers > 0) {
            log.info("Database already seeded.");
        } else
            log.info("Database seeded.");
    }

    private void grantIfMissing(String roleName, List<String[]> permissions) {
        Role role = this.roleRepository.findByName(roleName);
        if (role == null) {
            return;
        }
        for (String[] p : permissions) {
            Permission permission = this.permissionRepository.findByModuleAndApiPathAndMethod(p[0], p[1], p[2]);
            if (permission != null && role.getPermissions().stream().noneMatch(item -> item.getId() == permission.getId())) {
                role.getPermissions().add(permission);
                this.roleRepository.save(role);
            }
        }
    }

    private Role seedRole(String name, String description, List<String[]> permissions) {
        Role role = this.roleRepository.findByName(name);
        if (role != null) {
            return role;
        }
        List<Permission> granted = new ArrayList<>();
        for (String[] p : permissions) {
            Permission permission = this.permissionRepository.findByModuleAndApiPathAndMethod(p[0], p[1], p[2]);
            if (permission != null) {
                granted.add(permission);
            }
        }
        role = new Role();
        role.setName(name);
        role.setDescription(description);
        role.setActive(true);
        role.setPermissions(granted);
        return this.roleRepository.save(role);
    }
}
