package com.civsetu.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first Admin on startup when none exists. There is no default password in code:
 * without the environment variables the app starts, but logs that no Admin exists.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserService users;
    private final BootstrapAdminProperties properties;

    AdminBootstrap(UserService users, BootstrapAdminProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            if (!users.hasAdmin()) {
                log.warn("No Admin account exists. Set CIVSETU_BOOTSTRAP_ADMIN_EMAIL and "
                        + "CIVSETU_BOOTSTRAP_ADMIN_PASSWORD to create one on startup.");
            }
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "CIVSETU_BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String name = StringUtils.hasText(properties.name()) ? properties.name() : "CivSetu Admin";
        if (users.ensureBootstrapAdmin(name, properties.email(), properties.password())) {
            log.info("Created bootstrap Admin account for {}", UserService.normalizeEmail(properties.email()));
        }
    }
}
