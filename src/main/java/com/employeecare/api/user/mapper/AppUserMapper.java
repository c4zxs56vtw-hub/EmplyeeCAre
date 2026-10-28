package com.employeecare.api.user.mapper;
import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.user.entity.AppUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.ERROR

)
public interface AppUserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeeNumber", ignore = true)
    @Mapping(target = "jobTitle", source = "position")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)

    AppUser toEntity(CreateEmployeeRequest request);

}
