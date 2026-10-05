package vn.hoidanit.jobhunter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.UserIdentity;
import vn.hoidanit.jobhunter.oauth.OAuthProvider;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
