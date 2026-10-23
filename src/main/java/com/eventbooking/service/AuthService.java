package com.eventbooking.service;

import com.eventbooking.dao.UserDao;
import com.eventbooking.model.User;
import com.eventbooking.util.PasswordHasher;
import com.eventbooking.util.Validation;

import java.sql.SQLException;

public class AuthService {
    private final UserDao users;

    public AuthService(UserDao users) {
        this.users = users;
    }

    public User register(String fullName, String email, char[] password) throws SQLException {
        try {
            String name = Validation.requiredText(fullName, "Full name", 120);
            String validEmail = Validation.email(email);
            char[] validPassword = Validation.password(password);
            return users.create(name, validEmail, PasswordHasher.hash(validPassword), "USER");
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                throw new IllegalArgumentException("An account with that email address already exists.", ex);
            }
            throw ex;
        } finally {
            clear(password);
        }
    }

    public User login(String email, char[] password) throws SQLException {
        try {
            String validEmail = Validation.email(email);
            char[] validPassword = Validation.password(password);
            User user = users.findByEmail(validEmail);
            if (user == null || !PasswordHasher.verify(validPassword, user.passwordHash())) {
                throw new SecurityException("Email or password is incorrect.");
            }
            return user;
        } finally {
            clear(password);
        }
    }

    public void bootstrapAdminFromEnvironment() throws SQLException {
        String email = System.getenv("EVENTBOOKING_ADMIN_EMAIL");
        String password = System.getenv("EVENTBOOKING_ADMIN_PASSWORD");
        if ((email == null || email.isBlank()) && (password == null || password.isBlank())) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set both EVENTBOOKING_ADMIN_EMAIL and EVENTBOOKING_ADMIN_PASSWORD to bootstrap an administrator.");
        }
        String validEmail = Validation.email(email);
        char[] chars = Validation.password(password.toCharArray());
        try {
            User existing = users.findByEmail(validEmail);
            if (existing != null) {
                if (!"ADMIN".equals(existing.role())) {
                    throw new IllegalStateException("The configured administrator email already belongs to a regular user.");
                }
                return;
            }
            users.create("System Administrator", validEmail, PasswordHasher.hash(chars), "ADMIN");
            System.out.println("Administrator account created for " + validEmail + ".");
        } finally {
            clear(chars);
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
