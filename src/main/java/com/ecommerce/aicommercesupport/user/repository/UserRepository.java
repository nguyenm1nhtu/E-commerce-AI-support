package com.ecommerce.aicommercesupport.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.ecommerce.aicommercesupport.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
