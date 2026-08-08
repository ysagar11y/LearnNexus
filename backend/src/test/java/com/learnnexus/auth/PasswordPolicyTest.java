package com.learnnexus.auth;

import com.learnnexus.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Password policy")
class PasswordPolicyTest {

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
            "short1!",          // under the length floor
            "password",         // blocklisted, and no non-letter
            "1234567890",       // no letters
            "abcdefghijkl",     // no number or symbol
            "aaaaaaaaaaaa",     // a single repeated character
    })
    void rejectsWeakPasswords(String candidate) {
        assertThatThrownBy(() -> PasswordPolicy.validate(candidate, "someone@example.com"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("");
    }

    @Test
    @DisplayName("rejects a password containing the local part of the email")
    void rejectsPasswordsContainingTheEmail() {
        assertThatThrownBy(() -> PasswordPolicy.validate("priyanair-2026!", "priyanair@acme.test"))
                .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = {
            "Correct-Horse-9",
            "th1s is a long passphrase",
            "Learn@2026",
    })
    void acceptsReasonablePasswords(String candidate) {
        assertThatCode(() -> PasswordPolicy.validate(candidate, "someone@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("length alone is enough when mixed with a number")
    void favoursLengthOverCharacterClassGymnastics() {
        assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple 7", "a@b.com"))
                .doesNotThrowAnyException();
    }
}
