package com.learnnexus.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Sign in, token rotation and password lifecycle.")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Sign in to the tenant resolved from the host or X-Tenant header")
    @PostMapping("/login")
    public AuthDtos.SessionResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                          HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @Operation(summary = "Exchange a refresh token for a new session; the presented token is rotated out")
    @PostMapping("/refresh")
    public AuthDtos.SessionResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request,
                                            HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), httpRequest);
    }

    @Operation(summary = "Revoke the presented refresh token family")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke every refresh token for the signed-in user")
    @PostMapping("/logout-everywhere")
    public ResponseEntity<Void> logoutEverywhere() {
        authService.logoutEverywhere();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Profile of the signed-in user")
    @GetMapping("/me")
    public AuthDtos.ProfileResponse me() {
        return authService.currentProfile();
    }

    @Operation(summary = "Start a password reset. Always succeeds, so it cannot be used to discover accounts.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Complete a password reset using an emailed token")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activate an invited account and sign in")
    @PostMapping("/accept-invite")
    public AuthDtos.SessionResponse acceptInvite(@Valid @RequestBody AuthDtos.AcceptInviteRequest request,
                                                 HttpServletRequest httpRequest) {
        return authService.acceptInvite(request, httpRequest);
    }

    @Operation(summary = "Change the signed-in user's password; ends every other session")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
