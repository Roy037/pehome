package vn.hoidanit.jobhunter.domain.response.review;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResReviewDTO {
    private long id;
    private int rating;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
    private Ref user;
    private Ref company;
    // file name of the reviewer's profile picture (public under /storage/avatar), null when they have none
    private String userAvatar;
    // the reviewer currently holds a premium plan (shown as a VIP tag next to their name)
    private boolean vip;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Ref {
        private long id;
        private String name;
    }
}
