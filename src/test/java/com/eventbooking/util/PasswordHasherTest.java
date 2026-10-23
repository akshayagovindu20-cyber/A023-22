package com.eventbooking.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    @Test
    void hashesAreSaltedAndVerifyOnlyTheOriginalPassword() {
        char[] password = "correct horse battery".toCharArray();
        String first = PasswordHasher.hash(password);
        String second = PasswordHasher.hash(password);

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify(password, first));
        assertFalse(PasswordHasher.verify("incorrect password".toCharArray(), first));
        assertFalse(PasswordHasher.verify(password, "not-a-password-hash"));
    }
}
