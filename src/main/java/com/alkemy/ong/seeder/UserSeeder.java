package com.alkemy.ong.seeder;

import com.alkemy.ong.model.Role;
import com.alkemy.ong.repository.RoleRepository;
import com.alkemy.ong.repository.UserRepository;
import com.alkemy.ong.security.model.UserEntity;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Component
public class UserSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final Environment environment;

    public UserSeeder(UserRepository users, RoleRepository roles, PasswordEncoder passwords, Environment environment) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Role admin = ensureRole("ROLE_ADMIN", "Administration");
        ensureRole("ROLE_USER", "Access to your own data");
        if (!environment.acceptsProfiles(Profiles.of("demo")) || users.count() != 0) {
            return;
        }

        String password = environment.getRequiredProperty("ONG_DEMO_ADMIN_PASSWORD");
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Choose a demo password between 12 characters and 72 UTF-8 bytes");
        }
        users.save(new UserEntity("Demo", "Administrator", "admin@example.test", passwords.encode(password), "",
                Collections.singletonList(admin)));
    }

    private Role ensureRole(String name, String description) {
        Role role = roles.findByName(name);
        return role == null ? roles.save(new Role(name, description)) : role;
    }
}
