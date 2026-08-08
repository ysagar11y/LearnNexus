package com.learnnexus.iam;

import com.learnnexus.audit.AuditService;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.config.AppProperties;
import com.learnnexus.notification.MailService;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.security.JwtService;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final MailService mailService;
    private final AuditService auditService;
    private final TenantAwareJdbc jdbc;
    private final AppProperties properties;

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.Summary> list(String query, User.Status status, UUID orgUnitId,
                                               int page, int size, String sort) {
        Page<User> results = userRepository.search(
                blankToNull(query), status, orgUnitId,
                PageRequest.of(page, Math.min(size, 200), sortOf(sort)));

        Map<UUID, String> unitNames = orgUnitNames();
        return PageResponse.of(results, user -> toSummary(user, unitNames));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.Summary> listForManager(UUID managerId, int page, int size) {
        User manager = requireUser(managerId);
        if (manager.getOrgUnitId() == null) {
            return PageResponse.single(List.of());
        }
        OrgUnit unit = orgUnitRepository.findById(manager.getOrgUnitId())
                .orElseThrow(() -> ApiException.notFound("Organisation unit", manager.getOrgUnitId()));

        Page<User> results = userRepository.findInSubtree(
                unit.subtreePath(), unit.getId(),
                PageRequest.of(page, Math.min(size, 200), Sort.by("firstName")));

        Map<UUID, String> unitNames = orgUnitNames();
        return PageResponse.of(results, user -> toSummary(user, unitNames));
    }

    @Transactional(readOnly = true)
    public UserDtos.Detail get(UUID userId) {
        return get(userId, null);
    }

    /**
     * @param inviteUrl attached only by {@link #create} and
     *                  {@link #resendInvitation}, which alone hold the raw
     *                  (unhashed) token right after minting it — the stored
     *                  hash cannot be turned back into a usable link.
     */
    private UserDtos.Detail get(UUID userId, String inviteUrl) {
        User user = requireUser(userId);
        Map<UUID, String> unitNames = orgUnitNames();

        String managerName = user.getManagerId() == null ? null
                : userRepository.findActiveById(user.getManagerId()).map(User::displayName).orElse(null);

        return new UserDtos.Detail(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.displayName(),
                user.getJobTitle(), user.getPhone(), user.getAvatarUrl(), user.getStatus(), user.roleSet(),
                user.getOrgUnitId(), unitNames.get(user.getOrgUnitId()), user.getManagerId(), managerName,
                user.getLocale(), user.getTimezone(), user.isMfaEnabled(), user.getLastLoginAt(),
                user.getCreatedAt(), learningSnapshot(user.getId()), inviteUrl);
    }

    /**
     * Matches the link {@code MailService.sendInvitation} emails, so the copy
     * button in the admin console and the emailed link are always the same URL.
     */
    private String buildInviteUrl(TenantContext.Snapshot tenant, String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return properties.publicBaseUrl() + "/accept-invite?token=" + encodedToken + "&tenant=" + tenant.slug();
    }

    @Transactional(readOnly = true)
    public List<UserDtos.Summary> findByRole(RoleCode role) {
        Map<UUID, String> unitNames = orgUnitNames();
        return userRepository.findByRole(role.name()).stream()
                .map(user -> toSummary(user, unitNames))
                .toList();
    }

    /**
     * Aggregates a learner's record in one round trip. Expressed in SQL because
     * the equivalent JPA would be four separate count queries per user.
     */
    private UserDtos.LearningSnapshot learningSnapshot(UUID userId) {
        String sql = """
                select
                    count(*)                                                              as enrolled,
                    count(*) filter (where e.status = 'COMPLETED')                        as completed,
                    count(*) filter (where e.status = 'ACTIVE' and e.due_at < now())      as overdue,
                    coalesce(round(avg(e.progress_percent)), 0)                           as avg_progress,
                    (select count(*) from certificates c
                      where c.tenant_id = e.tenant_id and c.user_id = e.user_id and c.revoked_at is null) as certificates
                from enrollments e
                where e.tenant_id = ? and e.user_id = ?
                group by e.tenant_id, e.user_id
                """;
        return jdbc.queryOne(sql, (rs, rowNum) -> new UserDtos.LearningSnapshot(
                rs.getLong("enrolled"),
                rs.getLong("completed"),
                rs.getLong("overdue"),
                rs.getLong("certificates"),
                rs.getInt("avg_progress")
        ), userId).orElse(new UserDtos.LearningSnapshot(0, 0, 0, 0, 0));
    }

    // -----------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------

    @Transactional
    public UserDtos.Detail create(UserDtos.CreateRequest request) {
        TenantContext.Snapshot tenant = TenantContext.require();
        assertSeatAvailable(tenant);

        userRepository.findByEmail(request.email()).ifPresent(existing -> {
            throw ApiException.conflict("email_taken", "Someone with that email already belongs to this workspace.");
        });
        assertAssignableRoles(request.roles());
        validateOrgUnit(request.orgUnitId());

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setFirstName(request.firstName().trim());
        user.setLastName(blankToNull(request.lastName()));
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setRoleSet(request.roles());
        user.setOrgUnitId(request.orgUnitId());
        user.setManagerId(request.managerId());
        user.setStatus(User.Status.INVITED);

        String inviteToken = null;
        if (request.sendInvitation()) {
            inviteToken = jwtService.generateRefreshToken();
            user.setInviteTokenHash(jwtService.hashToken(inviteToken));
            user.setInviteExpiresAt(Instant.now().plus(INVITE_TTL));
        }

        userRepository.save(user);

        if (inviteToken != null) {
            String inviter = CurrentUser.find().map(AppUserPrincipal::displayName).orElse(tenant.name());
            mailService.sendInvitation(user, tenant, inviteToken, inviter);
        }

        auditService.record(AuditService.USER_CREATED, "User", user.getId(),
                "Added " + user.getEmail(),
                Map.of("roles", request.roles().stream().map(Enum::name).toList(),
                        "invited", request.sendInvitation()));

        return get(user.getId(), inviteToken == null ? null : buildInviteUrl(tenant, inviteToken));
    }

    @Transactional
    public UserDtos.Detail update(UUID userId, UserDtos.UpdateRequest request) {
        User user = requireUser(userId);
        validateOrgUnit(request.orgUnitId());

        if (request.managerId() != null && request.managerId().equals(userId)) {
            throw ApiException.badRequest("invalid_manager", "A user cannot report to themselves.");
        }

        if (request.firstName() != null) user.setFirstName(request.firstName().trim());
        if (request.lastName() != null) user.setLastName(blankToNull(request.lastName()));
        if (request.jobTitle() != null) user.setJobTitle(blankToNull(request.jobTitle()));
        if (request.phone() != null) user.setPhone(blankToNull(request.phone()));
        if (request.avatarUrl() != null) user.setAvatarUrl(blankToNull(request.avatarUrl()));
        if (request.locale() != null) user.setLocale(blankToNull(request.locale()));
        if (request.timezone() != null) user.setTimezone(blankToNull(request.timezone()));
        user.setOrgUnitId(request.orgUnitId());
        user.setManagerId(request.managerId());
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);
        auditService.record(AuditService.USER_UPDATED, "User", userId, "Updated " + user.getEmail());
        return get(userId);
    }

    @Transactional
    public UserDtos.Detail updateOwnProfile(UserDtos.ProfileUpdateRequest request) {
        User user = requireUser(CurrentUser.requireId());
        user.setFirstName(request.firstName().trim());
        user.setLastName(blankToNull(request.lastName()));
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setPhone(blankToNull(request.phone()));
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setLocale(blankToNull(request.locale()));
        user.setTimezone(blankToNull(request.timezone()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return get(user.getId());
    }

    @Transactional
    public UserDtos.Detail changeRoles(UUID userId, Set<RoleCode> roles) {
        User user = requireUser(userId);
        assertAssignableRoles(roles);
        assertNotLastAdmin(user, roles);

        Set<RoleCode> previous = user.roleSet();
        user.setRoleSet(roles);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Authorities are carried in the access token, so an existing session would
        // keep the old roles until it expired. Ending the sessions closes that gap.
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());

        auditService.record(AuditService.USER_ROLES_CHANGED, "User", userId,
                "Roles changed for " + user.getEmail(),
                Map.of("from", previous.stream().map(Enum::name).toList(),
                        "to", roles.stream().map(Enum::name).toList()));
        return get(userId);
    }

    @Transactional
    public UserDtos.Detail changeStatus(UUID userId, User.Status status) {
        User user = requireUser(userId);
        if (status == User.Status.SUSPENDED) {
            assertNotLastAdmin(user, Set.of());
            refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        }
        if (status == User.Status.ACTIVE && user.getPasswordHash() == null) {
            throw ApiException.badRequest("invite_pending",
                    "This user has not accepted their invitation yet. Resend the invitation instead.");
        }
        user.setStatus(status);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditService.record(AuditService.USER_UPDATED, "User", userId,
                "Status set to " + status + " for " + user.getEmail());
        return get(userId);
    }

    @Transactional
    public UserDtos.Detail resendInvitation(UUID userId) {
        TenantContext.Snapshot tenant = TenantContext.require();
        User user = requireUser(userId);
        if (user.getStatus() != User.Status.INVITED) {
            throw ApiException.badRequest("already_active", "This user has already activated their account.");
        }
        String token = jwtService.generateRefreshToken();
        user.setInviteTokenHash(jwtService.hashToken(token));
        user.setInviteExpiresAt(Instant.now().plus(INVITE_TTL));
        userRepository.save(user);

        String inviter = CurrentUser.find().map(AppUserPrincipal::displayName).orElse(tenant.name());
        mailService.sendInvitation(user, tenant, token, inviter);
        auditService.record(AuditService.USER_UPDATED, "User", userId, "Invitation resent to " + user.getEmail());

        return get(userId, buildInviteUrl(tenant, token));
    }

    @Transactional
    public void deactivate(UUID userId) {
        User user = requireUser(userId);
        if (userId.equals(CurrentUser.requireId())) {
            throw ApiException.badRequest("self_delete", "You cannot remove your own account.");
        }
        assertNotLastAdmin(user, Set.of());

        // Soft delete: completion records and certificates must survive, both for
        // compliance evidence and because reports reference the learner.
        user.setDeletedAt(Instant.now());
        user.setStatus(User.Status.SUSPENDED);
        // Free the address so it can be re-invited without colliding with the
        // partial unique index on live rows.
        user.setEmail(user.getEmail() + ".deleted." + System.currentTimeMillis());
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());

        auditService.record(AuditService.USER_DEACTIVATED, "User", userId, "Removed " + user.getEmail());
    }

    // -----------------------------------------------------------------
    // Bulk import
    // -----------------------------------------------------------------

    /**
     * Imports users from CSV with columns
     * {@code email, first_name, last_name, job_title, org_unit_code, roles}.
     * Existing addresses are updated rather than duplicated, so re-running an
     * export is idempotent.
     */
    @Transactional
    public UserDtos.ImportResult importCsv(MultipartFile file, boolean sendInvitations) {
        TenantContext.Snapshot tenant = TenantContext.require();
        Tenant tenantRecord = tenantRepository.findActiveById(tenant.tenantId())
                .orElseThrow(() -> ApiException.notFound("Tenant", tenant.tenantId()));

        Map<String, UUID> unitsByCode = orgUnitRepository.findAllOrdered().stream()
                .filter(unit -> unit.getCode() != null)
                .collect(Collectors.toMap(unit -> unit.getCode().toLowerCase(), OrgUnit::getId, (a, b) -> a));

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        long seatsUsed = userRepository.countActive();

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true)
                     .build().parse(reader)) {

            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1;
                try {
                    String email = value(record, "email");
                    if (email == null || !email.contains("@")) {
                        errors.add("Row " + line + ": missing or invalid email");
                        skipped++;
                        continue;
                    }
                    email = email.toLowerCase();

                    Set<RoleCode> roles = parseRoles(value(record, "roles"));
                    UUID orgUnitId = Optional.ofNullable(value(record, "org_unit_code"))
                            .map(code -> unitsByCode.get(code.toLowerCase()))
                            .orElse(null);

                    Optional<User> existing = userRepository.findByEmail(email);
                    if (existing.isPresent()) {
                        User user = existing.get();
                        applyIfPresent(value(record, "first_name"), user::setFirstName);
                        applyIfPresent(value(record, "last_name"), user::setLastName);
                        applyIfPresent(value(record, "job_title"), user::setJobTitle);
                        if (orgUnitId != null) {
                            user.setOrgUnitId(orgUnitId);
                        }
                        user.setRoleSet(roles);
                        user.setUpdatedAt(Instant.now());
                        userRepository.save(user);
                        updated++;
                        continue;
                    }

                    if (seatsUsed >= tenantRecord.getMaxUsers()) {
                        errors.add("Row " + line + ": skipped, the workspace has no seats left");
                        skipped++;
                        continue;
                    }

                    User user = new User();
                    user.setEmail(email);
                    user.setFirstName(Optional.ofNullable(value(record, "first_name")).orElse(email.split("@")[0]));
                    user.setLastName(value(record, "last_name"));
                    user.setJobTitle(value(record, "job_title"));
                    user.setOrgUnitId(orgUnitId);
                    user.setRoleSet(roles);
                    user.setStatus(User.Status.INVITED);

                    String token = null;
                    if (sendInvitations) {
                        token = jwtService.generateRefreshToken();
                        user.setInviteTokenHash(jwtService.hashToken(token));
                        user.setInviteExpiresAt(Instant.now().plus(INVITE_TTL));
                    }
                    userRepository.save(user);
                    seatsUsed++;
                    created++;

                    if (token != null) {
                        mailService.sendInvitation(user, tenant, token, tenant.name());
                    }
                } catch (IllegalArgumentException ex) {
                    errors.add("Row " + line + ": " + ex.getMessage());
                    skipped++;
                }
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("import_failed", "The CSV file could not be read.");
        }

        auditService.record(AuditService.USER_CREATED, "User", null,
                "Bulk import: " + created + " created, " + updated + " updated",
                Map.of("created", created, "updated", updated, "skipped", skipped));

        // Cap the reported errors: a malformed file should not produce a megabyte of JSON.
        return new UserDtos.ImportResult(created, updated, skipped,
                errors.size() > 50 ? errors.subList(0, 50) : errors);
    }

    private Set<RoleCode> parseRoles(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of(RoleCode.LEARNER);
        }
        Set<RoleCode> roles = new LinkedHashSet<>();
        for (String part : raw.split("[|,;]")) {
            String code = part.trim().toUpperCase().replace(' ', '_');
            if (code.isEmpty()) {
                continue;
            }
            try {
                RoleCode role = RoleCode.valueOf(code);
                if (role == RoleCode.PLATFORM_ADMIN) {
                    throw new IllegalArgumentException("PLATFORM_ADMIN cannot be granted by import");
                }
                roles.add(role);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unknown role '" + part.trim() + "'");
            }
        }
        return roles.isEmpty() ? Set.of(RoleCode.LEARNER) : roles;
    }

    // -----------------------------------------------------------------
    // Guards and helpers
    // -----------------------------------------------------------------

    private void assertSeatAvailable(TenantContext.Snapshot tenant) {
        Tenant record = tenantRepository.findActiveById(tenant.tenantId())
                .orElseThrow(() -> ApiException.notFound("Tenant", tenant.tenantId()));
        if (userRepository.countActive() >= record.getMaxUsers()) {
            throw ApiException.conflict("seat_limit_reached",
                    "This workspace has used all " + record.getMaxUsers() + " of its seats. Upgrade the plan to add more.");
        }
    }

    /** Only a platform super-admin may hand out the platform role. */
    private void assertAssignableRoles(Set<RoleCode> roles) {
        if (roles.contains(RoleCode.PLATFORM_ADMIN)
                && !CurrentUser.require().hasRole(RoleCode.PLATFORM_ADMIN)) {
            throw ApiException.forbidden("Only a platform administrator can grant the platform admin role.");
        }
    }

    /**
     * Refuses a change that would leave the workspace with no administrator and
     * therefore no way back in.
     */
    private void assertNotLastAdmin(User user, Set<RoleCode> incomingRoles) {
        boolean wasAdmin = user.hasRole(RoleCode.TENANT_ADMIN);
        boolean staysAdmin = incomingRoles.contains(RoleCode.TENANT_ADMIN);
        if (!wasAdmin || staysAdmin) {
            return;
        }
        long remainingAdmins = userRepository.findByRole(RoleCode.TENANT_ADMIN.name()).stream()
                .filter(candidate -> candidate.getStatus() == User.Status.ACTIVE)
                .filter(candidate -> !candidate.getId().equals(user.getId()))
                .count();
        if (remainingAdmins == 0) {
            throw ApiException.conflict("last_admin",
                    "This is the workspace's only administrator. Promote someone else first.");
        }
    }

    private void validateOrgUnit(UUID orgUnitId) {
        if (orgUnitId != null && orgUnitRepository.findById(orgUnitId).isEmpty()) {
            throw ApiException.badRequest("unknown_org_unit", "That organisation unit does not exist.");
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private Map<UUID, String> orgUnitNames() {
        Map<UUID, String> names = new HashMap<>();
        orgUnitRepository.findAllOrdered().forEach(unit -> names.put(unit.getId(), unit.getName()));
        return names;
    }

    private UserDtos.Summary toSummary(User user, Map<UUID, String> unitNames) {
        return new UserDtos.Summary(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.displayName(),
                user.getJobTitle(), user.getAvatarUrl(), user.getStatus(), user.roleSet(),
                user.getOrgUnitId(), unitNames.get(user.getOrgUnitId()),
                user.getLastLoginAt(), user.getCreatedAt());
    }

    private static Sort sortOf(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = switch (parts[0]) {
            case "name", "firstName" -> "firstName";
            case "email" -> "email";
            case "lastLogin" -> "lastLoginAt";
            case "status" -> "status";
            default -> "createdAt";
        };
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private static void applyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static String value(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String raw = record.get(column);
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
