package vn.hoidanit.jobhunter.controller;

import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.service.UserService;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
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

    UserController(UserService userService) {
        this.userService = userService;
    }

    // @GetMapping("/user/create")
    @PostMapping("/user/create")
    public User createNewUser(
            @RequestBody User postManUser) {

        User User = this.userService.handleCreateUser(postManUser);
        return User;
    }

    @DeleteMapping("/user/{id}")

    public String deleteUser(@PathVariable("id") long id) {
        this.userService.handleDeleteUser(id);
        return "minh";
    }

    // fetch user by id
    @GetMapping("/user/{id}")
    public User getUserById(@PathVariable("id") long id) {
        return this.userService.fetchUserById(id);
    }

    // fetch all user
    @GetMapping("/user")
    public List<User> getAllUser() {
        return this.userService.fetchAllUser();
    }

    // update
    @PutMapping("/user/{id}")
    public User updateUser(@RequestBody User user) {
        User User = this.userService.handleCreateUser(user);
        return User;
    }
}
