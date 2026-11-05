package com.carddemo.online.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityRepository extends JpaRepository<UserSecurity, String> {
}
