package com.fpt.servicecontract.auth.repository;

import com.fpt.servicecontract.auth.dto.UserInterface;
import com.fpt.servicecontract.auth.model.Permission;
import com.fpt.servicecontract.auth.model.Role;
import com.fpt.servicecontract.auth.model.User;
import com.fpt.servicecontract.auth.model.UserStatus;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findById(String id);

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    @Query(value = """
        SELECT u.id,u.name, u.email, u.address,
                                  u.identification_number, u.status, u.department, u.phone, u.position, u.avatar,
                                  CONCAT('[', GROUP_CONCAT(up.permissions SEPARATOR ','), ']') AS permissions
                           FROM users u
                           JOIN user_permissions up ON u.id = up.user_id where
                                           (lower(u.name) like lower(:name) or :name is null)
                                           and (lower(u.email) like lower(:email) or :email is null)
                                           and (lower(u.address) like lower(:address) or :address is null)
                                           and (lower(u.identification_number) like lower(:identificationNumber) or :identificationNumber is null)
                                           and (lower(u.status) like lower(:status) or :status is null)
                                           and (lower(u.department) like lower(:department) or :department is null)
                                           and (lower(u.phone) like lower(:phoneNumber) or :department is null)
                                           and (lower(u.position) like lower(:position) or :department is null)
                                           and (lower(u.role) like lower(:role) or :role is null)
                           GROUP BY u.id, u.name, u.email, u.address,
                                    u.identification_number, u.status, u.department, u.phone, u.position
        """
            , nativeQuery = true)
    Page<UserInterface> search(@Param("name") String name,
                               @Param("email") String email,
                               @Param("address") String address,
                               @Param("identificationNumber") String identificationNumber,
                               @Param("status") UserStatus status,
                               @Param("department") String department,
                               @Param("phoneNumber") String phoneNumber,
                               @Param("position") String position,
                               @Param("role") Role role,
                               Pageable pageable);
}
