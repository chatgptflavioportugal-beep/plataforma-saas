package com.saas.profile.repository;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Integração externa de envio de e-mail (Quarkus Mailer). Não é acesso a banco — por
 * isso vive em {@code repository/} (distinto de {@code dao/}, que é exclusivamente
 * persistência) com o sufixo Repository permitido para integrações externas reais.
 */
@ApplicationScoped
public class EmailRepository {

    private static final Logger LOG = Logger.getLogger(EmailRepository.class);

    @Inject
    Mailer mailer;

    public void sendInvitationEmail(String to, String tenantName, String accessLevelName, String inviteLink) {
        String subject = "Você foi convidado para " + tenantName;
        String html = buildInvitationEmailHtml(tenantName, accessLevelName, inviteLink);

        LOG.infof("Convite enviado para %s | tenant=%s | link=%s", to, tenantName, inviteLink);

        try {
            mailer.send(Mail.withHtml(to, subject, html));
        } catch (Exception e) {
            // Em dev com mock=true o envio é apenas logado acima.
            // Em prod, configure SMTP via variáveis de ambiente.
            LOG.warnf("Falha ao enviar e-mail de convite para %s: %s", to, e.getMessage());
        }
    }

    private String buildInvitationEmailHtml(String tenantName, String accessLevelName, String inviteLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f9fafb; padding: 32px;">
                  <div style="max-width: 480px; margin: 0 auto; background: white; border-radius: 12px;
                              padding: 32px; border: 1px solid #e5e7eb;">
                    <h2 style="color: #111827; margin-top: 0;">Você foi convidado!</h2>
                    <p style="color: #6b7280;">
                      Você foi convidado para participar de <strong style="color: #111827;">%s</strong>
                      com nível de acesso <strong style="color: #111827;">%s</strong>.
                    </p>
                    <a href="%s"
                       style="display: inline-block; margin-top: 16px; padding: 12px 24px;
                              background: #4f46e5; color: white; text-decoration: none;
                              border-radius: 8px; font-weight: 600;">
                      Aceitar convite
                    </a>
                    <p style="color: #9ca3af; font-size: 12px; margin-top: 24px;">
                      Se você não esperava este convite, pode ignorar este e-mail.<br/>
                      O link expira em 7 dias.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(tenantName, accessLevelName, inviteLink);
    }
}
