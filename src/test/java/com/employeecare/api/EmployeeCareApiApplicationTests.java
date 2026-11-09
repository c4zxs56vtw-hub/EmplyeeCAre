package com.employeecare.api;

import com.employeecare.api.auth.security.JwtConfig;
import com.employeecare.api.auth.security.PasswordEncoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                JwtConfig.class,
                PasswordEncoderConfig.class
        },
        properties = {
                "employeecare.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "employeecare.jwt.issuer=http://localhost:9090/api"
        }
)
class EmployeeCareApiApplicationTests {

    @Test
    void contextLoads() {
        assertThat(EmployeeCareApiApplication.class).isNotNull();
    }

}
