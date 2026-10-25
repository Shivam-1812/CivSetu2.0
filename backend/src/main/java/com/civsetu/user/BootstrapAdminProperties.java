package com.civsetu.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First Admin account, read from CIVSETU_BOOTSTRAP_ADMIN_* environment variables.
 */
@ConfigurationProperties("civsetu.bootstrap-admin")
public record BootstrapAdminProperties(String name, String email, String password) {
}
