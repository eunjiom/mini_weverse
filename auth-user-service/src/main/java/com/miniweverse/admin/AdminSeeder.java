package com.miniweverse.admin;

import com.miniweverse.admin.entity.Admin;
import com.miniweverse.admin.repository.AdminRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자는 회원가입 API가 없다. application-local.yml의 admin.seed-* 값으로
 * 앱 시작 시 없으면 하나 만들어둔다 (로컬/개발 환경 전용, 값이 없으면 그냥 건너뛴다).
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties properties;

    public AdminSeeder(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            AdminSeedProperties properties
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.seedEmail() == null || properties.seedEmail().isBlank()) {
            return;
        }
        if (adminRepository.findByEmail(properties.seedEmail()).isPresent()) {
            return;
        }
        Admin admin = Admin.create(properties.seedEmail(), passwordEncoder.encode(properties.seedPassword()));
        adminRepository.save(admin);
    }
}
