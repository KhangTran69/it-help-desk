package com.example.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailNotifierTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private User recipient(String email) {
        User user = new User("employee", "pw", "Employee", Role.EMPLOYEE);
        user.setEmail(email);
        return user;
    }

    @Test
    void enabledNotifierDeliversPlainMailToTheRecipientAddress() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailNotifier notifier = new MailNotifier(provider(sender), true, "no-reply@test.local");

        notifier.send(recipient("employee@test.local"), "Ticket #7 is overdue", "Please take a look.");

        ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getTo()).containsExactly("employee@test.local");
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getFrom())
                .isEqualTo("no-reply@test.local");
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getSubject())
                .isEqualTo("Ticket #7 is overdue");
    }

    @Test
    void disabledNotifierSendsNothing() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailNotifier notifier = new MailNotifier(provider(sender), false, "no-reply@test.local");

        notifier.send(recipient("employee@test.local"), "Subject", "Body");

        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void recipientWithoutEmailIsSkipped() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailNotifier notifier = new MailNotifier(provider(sender), true, "no-reply@test.local");
        User noEmail = new User("employee", "pw", "Employee", Role.EMPLOYEE);

        notifier.send(noEmail, "Subject", "Body");

        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void missingMailSenderConfigurationIsNotAnError() {
        ObjectProvider<JavaMailSender> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        MailNotifier notifier = new MailNotifier(emptyProvider, true, "no-reply@test.local");

        assertThatCode(() -> notifier.send(recipient("employee@test.local"), "Subject", "Body"))
                .doesNotThrowAnyException();
    }
}
