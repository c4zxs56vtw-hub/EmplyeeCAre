package com.employeecare.api.user.repository;

import com.employeecare.api.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

}