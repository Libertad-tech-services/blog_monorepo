package br.com.libertadfacilities.blog.config;

import br.com.libertadfacilities.blog.entity.User;
import br.com.libertadfacilities.blog.enums.UserRole;
import br.com.libertadfacilities.blog.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Slf4j
@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner createAdminIfNotExist(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.initial-admin.email:}")
            String configuredEmail,
            @Value("${app.initial-admin.password:}")
            String configuredPassword,
            @Value("${app.initial-admin.name:Administrador}")
            String configuredName
    ) {
        return args -> {
            removeLegacyAdminsWithDefaultPassword(
                    userRepository,
                    passwordEncoder
            );

            String adminEmail = configuredEmail == null
                    ? ""
                    : configuredEmail
                    .trim()
                    .toLowerCase(Locale.ROOT);

            String adminPassword = configuredPassword == null
                    ? ""
                    : configuredPassword;

            if (adminEmail.isBlank() && adminPassword.isBlank()) {
                log.warn(
                        "Bootstrap do administrador ignorado. " +
                                "Configure INITIAL_ADMIN_EMAIL e " +
                                "INITIAL_ADMIN_PASSWORD para criar a primeira conta."
                );
                return;
            }

            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                throw new IllegalStateException(
                        "INITIAL_ADMIN_EMAIL e INITIAL_ADMIN_PASSWORD " +
                                "devem ser configurados juntos."
                );
            }

            if (adminPassword.length() < 12) {
                throw new IllegalStateException(
                        "INITIAL_ADMIN_PASSWORD deve possuir " +
                                "pelo menos 12 caracteres."
                );
            }

            if (userRepository.existsByEmail(adminEmail)) {
                log.info(
                        "Bootstrap ignorado: o administrador " +
                                "configurado já existe."
                );
                return;
            }

            User admin = new User();
            admin.setName(
                    configuredName == null || configuredName.isBlank()
                            ? "Administrador"
                            : configuredName.trim()
            );
            admin.setEmail(adminEmail);
            admin.setPassword(
                    passwordEncoder.encode(adminPassword)
            );
            admin.setRole(UserRole.ADMIN);

            userRepository.save(admin);

            log.info("Administrador inicial criado com sucesso.");
        };
    }

    private void removeLegacyAdminsWithDefaultPassword(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        for (String legacyEmail : new String[]{
                "admin@empresa.com",
                "liberadLCS@gmail.com"
        }) {
            userRepository.findByEmail(legacyEmail)
                    .filter(user -> passwordEncoder.matches(
                            "admin123",
                            user.getPassword()
                    ))
                    .ifPresent(user -> {
                        userRepository.delete(user);

                        log.warn(
                                "Conta administrativa legada " +
                                        "com senha padrão foi removida."
                        );
                    });
        }
    }
}