package vn.hoidanit.jobhunter.service;

import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUpdateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqEmployerRegisterDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final RoleService roleService;
    private final ResumeRepository resumeRepository;
    private final PlanOrderRepository orderRepository;
    private final NotificationService notificationService;

    public UserService(
            UserRepository userRepository,
            CompanyService companyService,
            RoleService roleService,
            ResumeRepository resumeRepository,
            PlanOrderRepository orderRepository,
            NotificationService notificationService) {
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.roleService = roleService;
    }

    public List<User> fetchAllUser(Pageable pageable) {
        Page<User> pageUser = this.userRepository.findAll(pageable);
        return pageUser.getContent();
    }

    public void handleDeleteUser(long id) {
        if (this.orderRepository.existsByUserId(id)) {
            throw new ConflictException("Không thể xóa người dùng đã có giao dịch thanh toán.");
        }
        if (this.resumeRepository.existsByUserId(id)) {
            throw new ConflictException(
                    "Không thể xóa người dùng đã có hồ sơ ứng tuyển. Hãy xóa các hồ sơ ứng tuyển của người dùng này trước.");
        }
        this.userRepository.deleteById(id);
    }

    // Locking ends the session (the refresh token is dropped); an admin account can never be locked, so nobody is shut out of administration.
    @Transactional
    public User setLocked(long id, boolean locked, String actingEmail) {
        User user = fetchUserById(id);
        if (user == null) {
            throw new ResourceNotFoundException("User với id = " + id + " không tồn tại");
        }
        if (locked) {
            if (isSuperAdmin(user)) {
                throw new ConflictException("Không thể khóa tài khoản quản trị viên.");
            }
            if (user.getEmail().equalsIgnoreCase(actingEmail)) {
                throw new ConflictException("Không thể tự khóa tài khoản đang đăng nhập.");
            }
            user.setRefreshToken(null);
        }
        boolean changed = user.isLocked() != locked;
        user.setLocked(locked);
        user = this.userRepository.save(user);
        if (changed) {
            if (locked) {
                this.notificationService.accountLocked(user);
            } else {
                this.notificationService.accountUnlocked(user);
            }
        }
        return user;
    }

    /** The signed-in user chose a new password: every session is ended (the refresh token goes), and the owner is told by e-mail. */
    @Transactional
    public void changePassword(User user, String encodedPassword) {
        user.setPassword(encodedPassword);
        user.setRefreshToken(null);
        this.userRepository.save(user);
        this.notificationService.passwordChanged(user);
    }

    public boolean isEmailExist(String email) {
        return this.userRepository.existsByEmail(email);
    }

    public User handleGetUserByUsername(String username) {
        return this.userRepository.findByEmail(username);
    }

    public User handleGetCurrentUser() throws IdInvalidException {
        User user = this.userRepository.findByEmail(SecurityUtil.getCurrentUserLogin().orElse(""));
        if (user == null) {
            throw new IdInvalidException("Bạn cần đăng nhập để thực hiện thao tác này");
        }
        return user;
    }

    // Candidate-only actions (apply, save, follow, review) are closed to employer accounts, which are tied to a company.
    public User currentCandidate() throws IdInvalidException, PermissionException {
        User user = handleGetCurrentUser();
        if (user.getCompany() != null) {
            throw new PermissionException("Tài khoản nhà tuyển dụng không dùng được tính năng này.");
        }
        return user;
    }

    public User currentUserOrNull() {
        return this.userRepository.findByEmail(SecurityUtil.getCurrentUserLogin().orElse(""));
    }

    public static boolean isSuperAdmin(User user) {
        return user != null && user.getRole() != null && "SUPER_ADMIN".equals(user.getRole().getName());
    }

    // Company of the signed-in user; null for admins and candidates, who are not company-scoped.
    public Company currentUserCompany() {
        User user = this.userRepository.findByEmail(SecurityUtil.getCurrentUserLogin().orElse(""));
        return user == null ? null : user.getCompany();
    }

    @Transactional
    public User createEmployer(ReqEmployerRegisterDTO req, String encodedPassword) throws IdInvalidException {
        Role hr = this.roleService.fetchByName("HR");
        if (hr == null) {
            throw new IdInvalidException("Hệ thống chưa cấu hình vai trò nhà tuyển dụng");
        }
        Company company = new Company();
        company.setName(req.getCompanyName().trim());
        company.setAddress(req.getCompanyAddress().trim());
        company.setApproved(false);
        company = this.companyService.handleCreateCompany(company);

        User user = new User();
        user.setName(req.getName().trim());
        user.setEmail(req.getEmail().trim());
        user.setPassword(encodedPassword);
        user.setRole(hr);
        user.setCompany(company);
        user.setEmailVerified(false);
        return this.userRepository.save(user);
    }

    public User fetchUserById(long id) {
        Optional<User> userOptional = this.userRepository.findById(id);
        if (userOptional.isPresent()) {
            return userOptional.get();// neu co tra ra doi tuong
        }
        // optional nghia la c hay khong,de check xem co hay khong user,null
        return null;// khong thi thoi
    }

    public User handleCreateUser(User user) {
        if (user.getCompany() != null) {
            Optional<Company> companyOptional = this.companyService.findById(user.getCompany().getId());
            user.setCompany(companyOptional.isPresent() ? companyOptional.get() : null);
        }
        // check role
        if (user.getRole() != null) {
            Role r = this.roleService.fetchById(user.getRole().getId());
            user.setRole(r != null ? r : null);
        }
        return this.userRepository.save(user);
    }

    public User handleUpdateUser(User reqUser) {
        User currentUser = this.fetchUserById(reqUser.getId());
        if (currentUser != null) {
            currentUser.setAddress(reqUser.getAddress());
            currentUser.setGender(reqUser.getGender());
            currentUser.setAge(reqUser.getAge());
            currentUser.setName(reqUser.getName());

            // save
            currentUser = this.userRepository.save(currentUser);
            // create and save => upsert, update va insert, khi chua co data thi create
            // check company
            if (reqUser.getCompany() != null) {
                Optional<Company> companyOptional = this.companyService.findById(reqUser.getCompany().getId());
                currentUser.setCompany(companyOptional.isPresent() ? companyOptional.get() : null);
            }
            // update
            if (reqUser.getRole() != null) {
                Role r = this.roleService.fetchById(reqUser.getRole().getId());
                currentUser.setRole(r);
            }
            currentUser = this.userRepository.save(currentUser);
        }
        return currentUser;
    }

    public ResultPaginationDTO fetchAllUser(Specification<User> spec, Pageable pageable) {
        Page<User> pageUser = this.userRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        rs.setMeta(mt);

        // remove sensitive data
        List<ResUserDTO> listUser = pageUser.getContent()
                .stream().map(item -> this.convertToResUserDTO(item))
                .collect(Collectors.toList());

        rs.setResult(listUser);

        return rs;
    }

    public ResCreateUserDTO convertToResCreateUserDTO(User user) {
        ResCreateUserDTO res = new ResCreateUserDTO();
        ResCreateUserDTO.CompanyUser com = new ResCreateUserDTO.CompanyUser();
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setName(user.getName());
        res.setAge(user.getAge());
        res.setCreatedAt(user.getCreatedAt());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        if (user.getCompany() != null) {
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }
        return res;
    }

    public ResUserDTO convertToResUserDTO(User user) {
        ResUserDTO res = new ResUserDTO();
        ResUserDTO.CompanyUser com = new ResUserDTO.CompanyUser();
        ResUserDTO.RoleUser roleUser = new ResUserDTO.RoleUser();
        if (user.getCompany() != null) {
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }
        if (user.getRole() != null) {
            roleUser.setId(user.getRole().getId());
            roleUser.setName(user.getRole().getName());
            res.setRole(roleUser);
        }
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setName(user.getName());
        res.setAge(user.getAge());
        res.setUpdatedAt(user.getUpdatedAt());
        res.setCreatedAt(user.getCreatedAt());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setLocked(user.isLocked());
        return res;
    }

    public ResUpdateUserDTO convertToResUpdateUserDTO(User user) {
        ResUpdateUserDTO res = new ResUpdateUserDTO();
        ResUpdateUserDTO.CompanyUser com = new ResUpdateUserDTO.CompanyUser();
        if (user.getCompany() != null) {
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }
        res.setId(user.getId());
        res.setName(user.getName());
        res.setAge(user.getAge());
        res.setUpdatedAt(user.getUpdatedAt());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        return res;
    }

    public void updateUserToken(String token, String email) {
        User currentUser = this.handleGetUserByUsername(email);
        if (currentUser != null) {
            currentUser.setRefreshToken(token == null ? null : SecurityUtil.sha256(token));
            this.userRepository.save(currentUser);
        }
    }

    public User getUserByRefreshTokenAndEmail(String token, String email) {
        return this.userRepository.findByRefreshTokenAndEmail(SecurityUtil.sha256(token), email);
    }
}
