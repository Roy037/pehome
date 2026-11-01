package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqEmployerRegisterDTO;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;

class EmployerTermsTests {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static ReqEmployerRegisterDTO signUp(boolean accept) {
        ReqEmployerRegisterDTO req = new ReqEmployerRegisterDTO();
        req.setCompanyName("Acme");
        req.setCompanyAddress("Hà Nội");
        req.setName("Hà");
        req.setEmail("hr@acme.vn");
        req.setPassword("secret1");
        req.setTaxCode("0312345678");
        req.setPhone("0901234567");
        req.setAcceptTerms(accept);
        return req;
    }

    @Test
    void signUpNeedsTheTermsToBeAccepted() {
        assertTrue(VALIDATOR.validate(signUp(true)).isEmpty());
        var problems = VALIDATOR.validate(signUp(false));
        assertEquals(1, problems.size());
        assertEquals("acceptTerms", problems.iterator().next().getPropertyPath().toString());
    }

    @Test
    void onlyEmployersWhoHaveNotAcceptedTheCurrentVersionAreAsked() {
        User candidate = new User();
        assertFalse(ResLoginDTO.UserLogin.from(candidate).isTermsRequired(), "candidates have no employer terms");

        User employer = new User();
        employer.setCompany(new Company());
        assertTrue(ResLoginDTO.UserLogin.from(employer).isTermsRequired(), "never accepted");

        employer.setTermsVersion("2020-01");
        assertTrue(ResLoginDTO.UserLogin.from(employer).isTermsRequired(), "accepted an older version");

        employer.setTermsVersion(User.TERMS_VERSION);
        assertFalse(ResLoginDTO.UserLogin.from(employer).isTermsRequired());
    }
}
