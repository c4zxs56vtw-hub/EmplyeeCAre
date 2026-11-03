package com.employeecare.api.responsable.repository;

import com.employeecare.api.responsable.entity.ResponsableAccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResponsableAccountRepository
        extends JpaRepository<ResponsableAccount, UUID> {

}
