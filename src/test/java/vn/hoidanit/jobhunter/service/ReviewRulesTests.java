package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Review;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqAdminReviewDTO;
import vn.hoidanit.jobhunter.domain.request.ReqReviewDTO;
import vn.hoidanit.jobhunter.domain.response.review.ResCompanyReviewsDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.ReviewRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

class ReviewRulesTests {
    ReviewRepository reviews = mock(ReviewRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    UserService users = mock(UserService.class);
    CompanyService companyService = mock(CompanyService.class);
    NotificationService notifications = mock(NotificationService.class);
    ReviewService service = new ReviewService(reviews, companies, users, companyService, mock(PlanService.class),
            notifications);
    User candidate = new User();
    Company company = new Company();

    @BeforeEach
    void setUp() throws Exception {
        candidate.setId(7);
        candidate.setName("An");
        company.setId(3);
        company.setName("VINFAST");
        company.setApproved(true);
        when(users.currentCandidate()).thenReturn(candidate);
        when(companies.findById(3L)).thenReturn(Optional.of(company));
        when(reviews.findByUserIdAndCompanyId(7L, 3L)).thenReturn(Optional.empty());
        when(reviews.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
    }

    static ReqReviewDTO review(int rating, String content) {
        ReqReviewDTO req = new ReqReviewDTO();
        req.setRating(rating);
        req.setContent(content);
        return req;
    }

    @Test
    void aSecondReviewOfTheSameCompanyEditsTheFirstAndDoesNotNotifyAgain() throws Exception {
        service.saveMine(3, review(4, "  Môi trường làm việc tốt  "));
        ArgumentCaptor<Review> first = ArgumentCaptor.forClass(Review.class);
        verify(reviews).save(first.capture());
        assertEquals("Môi trường làm việc tốt", first.getValue().getContent());
        verify(notifications).reviewReceived(first.getValue());

        when(reviews.findByUserIdAndCompanyId(7L, 3L)).thenReturn(Optional.of(first.getValue()));
        service.saveMine(3, review(2, "Lương trả chậm"));

        verify(reviews, times(2)).save(first.getValue());
        assertEquals(2, first.getValue().getRating());
        verify(notifications, times(1)).reviewReceived(any());
    }

    @Test
    void onlyApprovedCompaniesCanBeReviewed() {
        company.setApproved(false);

        assertThrows(ResourceNotFoundException.class, () -> service.saveMine(3, review(5, "Tốt")));
        verify(reviews, never()).save(any());
    }

    @Test
    void theAverageIsRoundedToOneDecimalAndEveryStarIsListed() throws Exception {
        when(companyService.isVisible(company)).thenReturn(true);
        when(reviews.countByRating(3L)).thenReturn(List.of(new Object[] { 5, 2L }, new Object[] { 4, 1L }));
        when(reviews.findByCompanyId(eq(3L), any(Pageable.class))).thenReturn(Page.empty());

        ResCompanyReviewsDTO dto = service.fetchByCompany(3, PageRequest.of(0, 10));

        assertEquals(4.7, dto.getAverage());
        assertEquals(3, dto.getTotal());
        assertEquals(List.of(5, 4, 3, 2, 1), List.copyOf(dto.getDistribution().keySet()));
        assertEquals(0L, dto.getDistribution().get(1));
    }

    @Test
    void aCompanyThatIsNotVisibleHasNoReviewPage() {
        when(companyService.isVisible(company)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.fetchByCompany(3, PageRequest.of(0, 10)));
    }

    @Test
    void anAdminCannotAddASecondReviewForTheSamePersonAndCompany() {
        when(users.fetchUserById(7L)).thenReturn(candidate);
        when(reviews.findByUserIdAndCompanyId(7L, 3L)).thenReturn(Optional.of(new Review()));
        ReqAdminReviewDTO req = new ReqAdminReviewDTO();
        req.setUserId(7L);
        req.setCompanyId(3L);
        req.setRating(5);
        req.setContent("Tốt");

        assertThrows(ConflictException.class, () -> service.adminCreate(req));
        verify(reviews, never()).save(any());
    }
}
