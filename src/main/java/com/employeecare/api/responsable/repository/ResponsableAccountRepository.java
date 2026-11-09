package com.employeecare.api.responsable.repository;

import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.user.entity.AccountStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ResponsableAccountRepository
        extends JpaRepository<ResponsableAccount, UUID> {

    @Query("""
        SELECT DISTINCT r
        FROM ResponsableAccount r

        JOIN r.user u
        JOIN u.roles role
        JOIN r.assignedCategories category

        WHERE r.active = true

          AND u.status = :accountStatus

          AND role.code = 'RESPONSABLE'

          AND r.department.id = :departmentId

          AND category = :categoryCode

          AND (
              :confidential = false
              OR r.confidentialHabilitation = true
          )
        """)
    List<ResponsableAccount> findEligibleForTicket(

            @Param("departmentId")
            UUID departmentId,

            @Param("categoryCode")
            String categoryCode,

            @Param("confidential")
            boolean confidential,

            @Param("accountStatus")
            AccountStatus accountStatus
    );

}
