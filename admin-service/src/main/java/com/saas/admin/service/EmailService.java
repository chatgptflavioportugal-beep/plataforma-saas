package com.saas.admin.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @Inject
    Mailer mailer;

    public boolean sendAdminUserCreatedEmail(String to, String tempPassword) {
        try {
            mailer.send(Mail.withHtml(to, "Seu acesso administrativo foi criado",
                buildAdminUserCreatedEmailHtml(to, tempPassword)));
            LOG.infof("E-mail de acesso admin enviado para %s", to);
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao enviar e-mail de criação admin para %s: %s", to, e.getMessage());
            return false;
        }
    }

    public boolean sendAdminUserPasswordResetEmail(String to, String tempPassword) {
        try {
            mailer.send(Mail.withHtml(to, "Sua senha administrativa foi redefinida",
                buildAdminPasswordResetEmailHtml(to, tempPassword)));
            LOG.infof("E-mail de reset admin enviado para %s", to);
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao enviar e-mail de reset admin para %s: %s", to, e.getMessage());
            return false;
        }
    }

    private String buildAdminUserCreatedEmailHtml(String email, String tempPassword) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f9fafb; padding: 32px;">
                  <div style="max-width: 480px; margin: 0 auto; background: white; border-radius: 12px;
                              padding: 32px; border: 1px solid #e5e7eb;">
                    <h2 style="color: #111827; margin-top: 0;">Seu acesso administrativo foi criado</h2>
                    <p style="color: #6b7280;">Olá,</p>
                    <p style="color: #6b7280;">Seu acesso administrativo à plataforma foi criado.</p>
                    <table style="width:100%%; border-collapse:collapse; margin: 16px 0;">
                      <tr>
                        <td style="color:#6b7280; padding: 8px 0; font-size:14px;">E-mail de acesso:</td>
                        <td style="color:#111827; font-weight:600; padding: 8px 0; font-size:14px;">%s</td>
                      </tr>
                      <tr>
                        <td style="color:#6b7280; padding: 8px 0; font-size:14px;">Senha provisória:</td>
                        <td style="font-family:monospace; background:#f3f4f6; padding: 6px 10px;
                                   border-radius:6px; color:#111827; font-weight:600; font-size:15px; letter-spacing:1px;">%s</td>
                      </tr>
                    </table>
                    <p style="color: #6b7280; font-size:13px;">
                      Acesse a área administrativa e altere sua senha após o primeiro login.
                    </p>
                    <p style="color: #9ca3af; font-size: 12px; margin-top: 24px;">
                      Se você não reconhece este acesso, ignore esta mensagem ou entre em contato com o administrador da plataforma.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(email, tempPassword);
    }

    private String buildAdminPasswordResetEmailHtml(String email, String tempPassword) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f9fafb; padding: 32px;">
                  <div style="max-width: 480px; margin: 0 auto; background: white; border-radius: 12px;
                              padding: 32px; border: 1px solid #e5e7eb;">
                    <h2 style="color: #111827; margin-top: 0;">Sua senha administrativa foi redefinida</h2>
                    <p style="color: #6b7280;">Olá,</p>
                    <p style="color: #6b7280;">Sua senha administrativa foi redefinida.</p>
                    <table style="width:100%%; border-collapse:collapse; margin: 16px 0;">
                      <tr>
                        <td style="color:#6b7280; padding: 8px 0; font-size:14px;">E-mail de acesso:</td>
                        <td style="color:#111827; font-weight:600; padding: 8px 0; font-size:14px;">%s</td>
                      </tr>
                      <tr>
                        <td style="color:#6b7280; padding: 8px 0; font-size:14px;">Nova senha provisória:</td>
                        <td style="font-family:monospace; background:#f3f4f6; padding: 6px 10px;
                                   border-radius:6px; color:#111827; font-weight:600; font-size:15px; letter-spacing:1px;">%s</td>
                      </tr>
                    </table>
                    <p style="color: #6b7280; font-size:13px;">
                      Use essa senha para acessar a área administrativa. Recomendamos alterar a senha após o login.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(email, tempPassword);
    }
}
