package com.learnnexus.notification;

import com.learnnexus.catalog.Course;
import com.learnnexus.common.PageResponse;
import com.learnnexus.iam.User;
import com.learnnexus.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * In-app notifications. Kept separate from {@link MailService}: an admin action
 * should always leave an in-app trace even when email is disabled or bounces.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    public record Item(
            UUID id,
            String event,
            String title,
            String body,
            String link,
            Notification.Severity severity,
            boolean read,
            Instant createdAt
    ) {}

    public record Inbox(
            PageResponse<Item> notifications,
            long unreadCount
    ) {}

    @Transactional
    public void push(UUID userId, String event, String title, String body, String link,
                     Notification.Severity severity) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setEvent(event);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setSeverity(severity);
        repository.save(notification);
    }

    @Transactional
    public void courseAssigned(User learner, Course course, Instant dueAt) {
        push(learner.getId(), "course.assigned",
                course.getTitle(),
                dueAt == null
                        ? "A new course has been added to your learning plan."
                        : "Due by " + dueAt.atZone(ZoneOffset.UTC).toLocalDate() + ".",
                "/learn/" + course.getId(),
                Notification.Severity.INFO);
    }

    @Transactional
    public void courseCompleted(User learner, Course course) {
        push(learner.getId(), "course.completed",
                "You finished " + course.getTitle(),
                course.isCertificateEnabled()
                        ? "Your certificate is ready in your wallet."
                        : "Nice work.",
                course.isCertificateEnabled() ? "/certificates" : "/learn/" + course.getId(),
                Notification.Severity.SUCCESS);
    }

    @Transactional
    public void deadlineApproaching(UUID userId, Course course, Instant dueAt) {
        push(userId, "course.due_soon",
                course.getTitle() + " is due soon",
                "Due by " + dueAt.atZone(ZoneOffset.UTC).toLocalDate() + ".",
                "/learn/" + course.getId(),
                Notification.Severity.WARNING);
    }

    @Transactional
    public void certificateIssued(UUID userId, String courseTitle) {
        push(userId, "certificate.issued",
                "Certificate issued",
                "Your certificate for " + courseTitle + " is ready to download.",
                "/certificates",
                Notification.Severity.SUCCESS);
    }

    @Transactional
    public void attemptGraded(UUID userId, String assessmentTitle, boolean passed, int percentage) {
        push(userId, "assessment.graded",
                assessmentTitle + " has been graded",
                (passed ? "You passed with " : "You scored ") + percentage + "%.",
                "/my-learning",
                passed ? Notification.Severity.SUCCESS : Notification.Severity.WARNING);
    }

    @Transactional(readOnly = true)
    public Inbox inbox(int page, int size) {
        UUID userId = CurrentUser.requireId();
        var notifications = repository.findInbox(userId, PageRequest.of(page, Math.min(size, 50)));
        return new Inbox(
                PageResponse.of(notifications, this::toItem),
                repository.countUnread(userId));
    }

    @Transactional
    public void markRead(UUID notificationId) {
        repository.findById(notificationId)
                .filter(notification -> notification.getUserId().equals(CurrentUser.requireId()))
                .ifPresent(notification -> {
                    notification.setReadAt(Instant.now());
                    repository.save(notification);
                });
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead(CurrentUser.requireId(), Instant.now());
    }

    private Item toItem(Notification notification) {
        return new Item(notification.getId(), notification.getEvent(), notification.getTitle(),
                notification.getBody(), notification.getLink(), notification.getSeverity(),
                notification.getReadAt() != null, notification.getCreatedAt());
    }
}
