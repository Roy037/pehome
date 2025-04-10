package vn.hoidanit.jobhunter.controller;

import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.util.List;
import java.util.Optional;

import org.hibernate.query.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    // @GetMapping("/users/create")
    @PostMapping("/users/create")
    public ResponseEntity<User> createNewUser(@RequestBody User postManUser) {
        String hashPassword = this.passwordEncoder.encode(postManUser.getPassword());
        postManUser.setPassword(hashPassword);
        /**
         * ma hoa mat khau. RequestBody lay data
         */
        User User = this.userService.handleCreateUser(postManUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(User);
        /**
         * tra ma loi
         */
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") long id) throws IdInvalidException {
        if (id >= 1500) {
            throw new IdInvalidException("khong lon hon 1500");
        }

        this.userService.handleDeleteUser(id);

        // return ResponseEntity.status(HttpStatus.OK).body("User");
        /**
         * ResponseEntity tra ma loi
         */

        return ResponseEntity.noContent().build();
        /**
         * khi xoa khong can phai phan hoi
         */
    }

    // fetch user by id
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable("id") long id) {
        User fetchUser = this.userService.fetchUserById(id);
        return ResponseEntity.status(HttpStatus.OK).body(fetchUser); // tra ma loi
    }

    // fetch all user
    @GetMapping("/users")
    public ResponseEntity<ResultPaginationDTO> getAllUser(
            // phan trang
            @RequestParam("current") Optional<String> currentOptional,
            @RequestParam("pageSize") Optional<String> pageSizeOptional) {
        String sCurrent = currentOptional.isPresent() ? currentOptional.get() : "";
        String sPageSize = pageSizeOptional.isPresent() ? pageSizeOptional.get() : "";
        // convert
        int current = Integer.parseInt(sCurrent);
        int pageSize = Integer.parseInt(sPageSize);
        Pageable pageable = PageRequest.of(current - 1, pageSize);
        // truyen vao service
        return ResponseEntity.status(HttpStatus.OK).body(this.userService.fetchAllUser(pageable)); // tra ma loi
    }

    // update
    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@RequestBody User user) {
        User User = this.userService.handleUpdateUser(user);
        return ResponseEntity.status(HttpStatus.OK).body(User); // tra ma loi
    }
}
