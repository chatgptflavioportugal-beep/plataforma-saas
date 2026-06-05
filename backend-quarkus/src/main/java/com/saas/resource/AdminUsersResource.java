package com.saas.resource;

import com.saas.service.EmailService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.*;

/**
 * Gestão de usuários administrativos da plataforma.
 * Separado de user_profiles/clientes — controla acesso à área Admin.
 */
@Path("/api/v1/admin/admin-users")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUsersResource {

    private static final Logger LOG = Logger.getLogger(AdminUsersResource.class);

    @Inject EntityManager em;
    @Inject JsonWebToken  jwt;
    @Inject AdminResource adminResource;
    @Inject EmailService  emailService;

    @ConfigProperty(name = "supabase.url")
    Optional<String> supabaseUrl;

    @ConfigProperty(name = "supabase.service-role-key")
    Optional<String> supabaseServiceRoleKey;

    // ─── Listagem ─────────────────────────────────────────────────────────────

    @GET
    @SuppressWarnings("unchecked")
    public Response listAdminUsers(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("access_level_id") String accessLevelId) {

        adminResource.requireAdminPermission("admin.users.view");

        StringBuilder sql = new StringBuilder(
            "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
            "up.admin_access_level_id::text, al.name AS access_level_name, " +
            "up.created_at::text, au.last_sign_in_at::text " +
            "FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "LEFT JOIN admin_access_levels al ON al.id = up.admin_access_level_id " +
            "WHERE up.system_role IN ('SUPER_ADMIN', 'ADMIN_USER')"
        );

        Map<String, Object> params = new LinkedHashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(up.full_name) LIKE LOWER(:search) OR LOWER(au.email) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(Boolean.parseBoolean(status) ? " AND up.is_active = TRUE" : " AND up.is_active = FALSE");
        }
        if (accessLevelId != null && !accessLevelId.isBlank()) {
            sql.append(" AND up.admin_access_level_id::text = :alId");
            params.put("alId", accessLevelId.trim());
        }
        sql.append(" ORDER BY up.system_role DESC, up.full_name");

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = (List<Object[]>) query.getResultList();

        var users = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",              r[0]);
            m.put("email",           r[1]);
            m.put("fullName",        r[2]);
            m.put("systemRole",      r[3]);
            m.put("isActive",        r[4]);
            m.put("accessLevelId",   r[5]);
            m.put("accessLevelName", r[6]);
            m.put("createdAt",       r[7]);
            m.put("lastSignInAt",    r[8]);
            return m;
        }).toList();

        return Response.ok(users).build();
    }

    // ─── Criar usuário administrativo ─────────────────────────────────────────

    @POST
    @Transactional
    @SuppressWarnings("unchecked")
    public Response createAdminUser(Map<String, Object> body) {
        adminResource.requireAdminPermission("admin.users.create");

        String email = (String) body.get("email");
        String fullName = (String) body.get("fullName");
        String accessLevelId = (String) body.get("accessLevelId");
        String tempPassword = (String) body.get("tempPassword");
        boolean sendPasswordEmail = !Boolean.FALSE.equals(body.get("sendPasswordEmail"));

        if (email == null || email.isBlank())
            return Response.status(400).entity(Map.of("error", "email é obrigatório")).build();
        if (fullName == null || fullName.isBlank())
            return Response.status(400).entity(Map.of("error", "fullName é obrigatório")).build();

        // Validar nível de acesso se informado
        if (accessLevelId != null && !accessLevelId.isBlank()) {
            long exists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM admin_access_levels WHERE id::text = :id AND status = 'ACTIVE'"
            ).setParameter("id", accessLevelId).getSingleResult()).longValue();
            if (exists == 0)
                return Response.status(400).entity(Map.of("error", "Nível de acesso não encontrado ou inativo")).build();
        }

        // Verificar se já existe user_profile com este email
        List<Object[]> existing = (List<Object[]>) em.createNativeQuery(
            "SELECT up.id::text, up.system_role FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id WHERE au.email = :email"
        ).setParameter("email", email.trim().toLowerCase()).getResultList();

        if (!existing.isEmpty()) {
            Object[] row = existing.get(0);
            String existingRole = (String) row[1];
            if ("SUPER_ADMIN".equals(existingRole))
                return Response.status(409).entity(Map.of("error", "Este e-mail pertence ao SUPER_ADMIN")).build();
            if ("ADMIN_USER".equals(existingRole))
                return Response.status(409).entity(Map.of("error", "Já existe um usuário administrativo com este e-mail")).build();
            // Bloquear promoção de conta cliente para admin — emails são exclusivos por área
            return Response.status(409).entity(Map.of("error",
                "Este e-mail já está cadastrado como usuário cliente. " +
                "Um e-mail não pode pertencer à área administrativa e à área cliente ao mesmo tempo."
            )).build();
        }

        // Definir senha efetiva antes de chamar o Supabase
        String effectivePassword = (tempPassword != null && !tempPassword.isBlank())
                ? tempPassword.trim()
                : generateTempPassword();

        // Criar usuário no Supabase via Admin API
        String newUserId = null;

        if (supabaseUrl.isPresent() && !supabaseUrl.get().isBlank()
                && supabaseServiceRoleKey.isPresent() && !supabaseServiceRoleKey.get().isBlank()) {
            try {
                newUserId = createSupabaseUser(email.trim().toLowerCase(), fullName.trim(), effectivePassword);
            } catch (Exception e) {
                return Response.status(500).entity(Map.of("error", "Erro ao criar usuário no sistema de autenticação: " + e.getMessage())).build();
            }
        }

        // Se o usuário já existe no Supabase mas não é admin, promovê-lo
        if (newUserId == null && !existing.isEmpty()) {
            newUserId = (String) existing.get(0)[0];
        }

        if (newUserId == null) {
            return Response.status(400).entity(Map.of(
                "error", "Não foi possível criar o usuário. Configure SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY, ou crie o usuário no Supabase primeiro."
            )).build();
        }

        // Atualizar user_profiles para marcar como ADMIN_USER
        String finalAccessLevelId = (accessLevelId != null && !accessLevelId.isBlank()) ? accessLevelId : null;

        if (finalAccessLevelId != null) {
            em.createNativeQuery(
                "UPDATE user_profiles SET system_role = 'ADMIN_USER', full_name = :name, " +
                "admin_access_level_id = CAST(:alId AS uuid), is_active = TRUE " +
                "WHERE id::text = :userId"
            ).setParameter("name", fullName.trim())
             .setParameter("alId", finalAccessLevelId)
             .setParameter("userId", newUserId)
             .executeUpdate();
        } else {
            em.createNativeQuery(
                "UPDATE user_profiles SET system_role = 'ADMIN_USER', full_name = :name, is_active = TRUE " +
                "WHERE id::text = :userId"
            ).setParameter("name", fullName.trim())
             .setParameter("userId", newUserId)
             .executeUpdate();
        }

        List<Object[]> result = (List<Object[]>) em.createNativeQuery(
            "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
            "up.admin_access_level_id::text, al.name, up.created_at::text " +
            "FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "LEFT JOIN admin_access_levels al ON al.id = up.admin_access_level_id " +
            "WHERE up.id::text = :userId"
        ).setParameter("userId", newUserId).getResultList();

        if (result.isEmpty())
            return Response.status(500).entity(Map.of("error", "Usuário criado mas não encontrado")).build();

        Object[] r = result.get(0);

        boolean emailSent = false;
        if (sendPasswordEmail) {
            emailSent = emailService.sendAdminUserCreatedEmail(email.trim().toLowerCase(), effectivePassword);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id",              r[0]);
        out.put("email",           r[1]);
        out.put("fullName",        r[2]);
        out.put("systemRole",      r[3]);
        out.put("isActive",        r[4]);
        out.put("accessLevelId",   r[5]);
        out.put("accessLevelName", r[6]);
        out.put("createdAt",       r[7]);
        out.put("tempPassword",    effectivePassword);
        out.put("emailSent",       emailSent);

        return Response.status(201).entity(out).build();
    }

    // ─── Editar usuário administrativo ────────────────────────────────────────

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateAdminUser(@PathParam("id") String id, Map<String, Object> body) {
        adminResource.requireAdminPermission("admin.users.edit");

        String fullName = (String) body.get("fullName");
        String accessLevelId = (String) body.get("accessLevelId");

        if (fullName == null || fullName.isBlank())
            return Response.status(400).entity(Map.of("error", "fullName é obrigatório")).build();

        // Não permitir editar SUPER_ADMIN
        long isSuperAdmin = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM user_profiles WHERE id::text = :id AND system_role = 'SUPER_ADMIN'"
        ).setParameter("id", id).getSingleResult()).longValue();
        if (isSuperAdmin > 0)
            return Response.status(403).entity(Map.of("error", "Não é permitido editar o SUPER_ADMIN por esta interface")).build();

        // Validar nível de acesso
        if (accessLevelId != null && !accessLevelId.isBlank()) {
            long exists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM admin_access_levels WHERE id::text = :id AND status = 'ACTIVE'"
            ).setParameter("id", accessLevelId).getSingleResult()).longValue();
            if (exists == 0)
                return Response.status(400).entity(Map.of("error", "Nível de acesso não encontrado ou inativo")).build();

            int updated = em.createNativeQuery(
                "UPDATE user_profiles SET full_name = :name, admin_access_level_id = CAST(:alId AS uuid) " +
                "WHERE id::text = :id AND system_role = 'ADMIN_USER'"
            ).setParameter("name", fullName.trim())
             .setParameter("alId", accessLevelId)
             .setParameter("id", id)
             .executeUpdate();
            if (updated == 0) return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();
        } else {
            int updated = em.createNativeQuery(
                "UPDATE user_profiles SET full_name = :name, admin_access_level_id = NULL " +
                "WHERE id::text = :id AND system_role = 'ADMIN_USER'"
            ).setParameter("name", fullName.trim())
             .setParameter("id", id)
             .executeUpdate();
            if (updated == 0) return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();
        }

        return Response.ok(Map.of("ok", true)).build();
    }

    // ─── Ativar / Inativar ────────────────────────────────────────────────────

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") String id, Map<String, Object> body) {
        adminResource.requireAdminPermission("admin.users.activate");

        Boolean isActive = body instanceof Map<?,?> ? (Boolean) body.get("isActive") : null;
        if (isActive == null)
            return Response.status(400).entity(Map.of("error", "isActive é obrigatório (true/false)")).build();

        // Não permitir inativar SUPER_ADMIN
        long isSuperAdmin = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM user_profiles WHERE id::text = :id AND system_role = 'SUPER_ADMIN'"
        ).setParameter("id", id).getSingleResult()).longValue();
        if (isSuperAdmin > 0)
            return Response.status(403).entity(Map.of("error", "Não é permitido inativar o SUPER_ADMIN")).build();

        // Não permitir que o usuário atual se inative
        String currentUserId = adminResource.currentUserId();
        if (id.equals(currentUserId) && Boolean.FALSE.equals(isActive))
            return Response.status(403).entity(Map.of("error", "Você não pode inativar sua própria conta")).build();

        int updated = em.createNativeQuery(
            "UPDATE user_profiles SET is_active = :isActive WHERE id::text = :id AND system_role = 'ADMIN_USER'"
        ).setParameter("isActive", isActive)
         .setParameter("id", id)
         .executeUpdate();

        if (updated == 0) return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();
        return Response.ok(Map.of("ok", true, "isActive", isActive)).build();
    }

    // ─── Reset de senha ───────────────────────────────────────────────────────

    @POST
    @Path("/{id}/reset-password")
    @SuppressWarnings("unchecked")
    public Response resetPassword(@PathParam("id") String id, Map<String, Object> body) {
        adminResource.requireAdminPermission("admin.users.reset_password");

        boolean sendPasswordEmail = !Boolean.FALSE.equals(body.get("sendPasswordEmail"));

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT au.email, up.system_role FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id WHERE up.id::text = :id"
        ).setParameter("id", id).getResultList();

        if (rows.isEmpty())
            return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();

        String userEmail  = (String) rows.get(0)[0];
        String systemRole = (String) rows.get(0)[1];

        if ("SUPER_ADMIN".equals(systemRole))
            return Response.status(403).entity(Map.of("error", "Não é permitido resetar a senha do SUPER_ADMIN")).build();

        boolean supabaseConfigured = supabaseUrl.isPresent() && !supabaseUrl.get().isBlank()
                && supabaseServiceRoleKey.isPresent() && !supabaseServiceRoleKey.get().isBlank();

        LOG.debugf("reset-password: userId=%s supabaseConfigured=%s", id, supabaseConfigured);

        if (!supabaseConfigured) {
            return Response.status(500).entity(Map.of(
                "error", "Reset de senha requer VITE_SUPABASE_URL e VITE_SUPABASE_SERVICE_ROLE_KEY configurados no backend."
            )).build();
        }

        String newPassword = generateTempPassword();

        try {
            updateSupabaseUserPassword(id, newPassword);
            LOG.infof("reset-password: senha atualizada no Supabase para userId=%s", id);
        } catch (Exception e) {
            LOG.errorf("reset-password: falha ao atualizar senha no Supabase para userId=%s — %s", id, e.getMessage());
            return Response.status(500).entity(Map.of("error", "Erro ao atualizar senha: " + e.getMessage())).build();
        }

        boolean emailSent = false;
        if (sendPasswordEmail) {
            emailSent = emailService.sendAdminUserPasswordResetEmail(userEmail, newPassword);
        }

        return Response.ok(Map.of(
            "success",           true,
            "message",           "Senha resetada com sucesso.",
            "temporaryPassword", newPassword,
            "emailSent",         emailSent,
            "email",             userEmail
        )).build();
    }

    // ─── Reenviar senha por e-mail ────────────────────────────────────────────

    @POST
    @Path("/{id}/send-password-email")
    @SuppressWarnings("unchecked")
    public Response sendPasswordEmailEndpoint(@PathParam("id") String id, Map<String, Object> body) {
        // Permite quem pode criar ou quem pode resetar senha
        boolean allowed = false;
        try { adminResource.requireAdminPermission("admin.users.create"); allowed = true; } catch (ForbiddenException ignored) {}
        if (!allowed) adminResource.requireAdminPermission("admin.users.reset_password");

        String password = (String) body.get("password");
        if (password == null || password.isBlank())
            return Response.status(400).entity(Map.of("error", "password é obrigatório")).build();

        String context = body.get("context") instanceof String s ? s : "created";

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT au.email FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "WHERE up.id::text = :id AND up.system_role IN ('SUPER_ADMIN', 'ADMIN_USER')"
        ).setParameter("id", id).getResultList();

        if (rows.isEmpty())
            return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();

        String userEmail = (String) rows.get(0)[0];
        boolean sent = "reset".equals(context)
            ? emailService.sendAdminUserPasswordResetEmail(userEmail, password)
            : emailService.sendAdminUserCreatedEmail(userEmail, password);

        if (!sent)
            return Response.status(500).entity(Map.of("error", "Falha ao enviar e-mail")).build();

        return Response.ok(Map.of("ok", true, "email", userEmail)).build();
    }

    // ─── Supabase Admin API ───────────────────────────────────────────────────

    private String createSupabaseUser(String email, String fullName, String tempPassword) throws Exception {
        String password = tempPassword;
        String url = supabaseUrl.orElseThrow();
        String key = supabaseServiceRoleKey.orElseThrow();

        String body = String.format(
            "{\"email\":\"%s\",\"password\":\"%s\",\"email_confirm\":true,\"user_metadata\":{\"full_name\":\"%s\"}}",
            email, password, fullName.replace("\"", "\\\"")
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url + "/auth/v1/admin/users"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + key)
            .header("apikey", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Supabase retornou status " + response.statusCode() + ": " + response.body());
        }

        // Extrair ID do JSON de resposta (parsing simples)
        String responseBody = response.body();
        int idStart = responseBody.indexOf("\"id\":\"") + 6;
        int idEnd = responseBody.indexOf("\"", idStart);
        if (idStart < 6 || idEnd < 0)
            throw new RuntimeException("Não foi possível extrair ID do usuário criado");

        return responseBody.substring(idStart, idEnd);
    }

    private void updateSupabaseUserPassword(String userId, String newPassword) throws Exception {
        String url = supabaseUrl.orElseThrow();
        String key = supabaseServiceRoleKey.orElseThrow();

        String targetUrl = url + "/auth/v1/admin/users/" + userId;
        LOG.debugf("updateSupabaseUserPassword: PUT %s", targetUrl);

        String reqBody = "{\"password\":\"" + newPassword.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + key)
            .header("apikey", key)
            .PUT(HttpRequest.BodyPublishers.ofString(reqBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        LOG.debugf("updateSupabaseUserPassword: status=%d body=%s", response.statusCode(), response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Supabase retornou status " + response.statusCode() + ": " + response.body());
        }
    }

    private String generateTempPassword() {
        String upper   = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String lower   = "abcdefghjkmnpqrstuvwxyz";
        String digits  = "23456789";
        String special = "!@#$%";
        String all     = upper + lower + digits + special;

        SecureRandom rnd = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        chars.add(upper.charAt(rnd.nextInt(upper.length())));
        chars.add(lower.charAt(rnd.nextInt(lower.length())));
        chars.add(digits.charAt(rnd.nextInt(digits.length())));
        chars.add(special.charAt(rnd.nextInt(special.length())));
        for (int i = 0; i < 8; i++) chars.add(all.charAt(rnd.nextInt(all.length())));
        Collections.shuffle(chars, rnd);

        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }
}
