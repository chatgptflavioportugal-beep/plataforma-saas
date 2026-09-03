package com.saas.admin.resource;

import com.saas.admin.dto.AdminUserCreatedDTO;
import com.saas.admin.dto.AdminUserDTO;
import com.saas.admin.dto.AdminUserStatusRequest;
import com.saas.admin.dto.CreateAdminUserRequest;
import com.saas.admin.dto.ResetPasswordRequest;
import com.saas.admin.dto.SendPasswordEmailRequest;
import com.saas.admin.dto.UpdateAdminUserRequest;
import com.saas.platformadmin.PlatformAdminAuthService;
import com.saas.admin.negocio.impl.AdminUsersNegocio;
import com.saas.admin.to.EmailRoleTO;
import com.saas.admin.to.ExistingUserTO;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gestão de usuários administrativos da plataforma.
 * Separado de user_profiles/clientes — controla acesso à área Admin.
 * Migrado 1:1 do backend-quarkus (com.saas.resource.AdminUsersResource).
 */
@Path("/api/v1/admin/admin-users")
@Tag(name = "Users", description = "Gestão dos usuários administrativos da plataforma (system_role SUPER_ADMIN/ADMIN_USER) — " +
    "separado dos clientes finais (user_profiles comuns). Todas as rotas deste controller pertencem " +
    "exclusivamente ao contexto administrativo e não devem ser utilizadas pelo ambiente cliente.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUsersResource {

    private static final Logger LOG = Logger.getLogger(AdminUsersResource.class);

    @Inject PlatformAdminAuthService adminAuth;
    @Inject AdminUsersNegocio usersNegocio;

    // ─── Listagem ─────────────────────────────────────────────────────────────

    @GET
    @Operation(
        summary = "Lista usuários administrativos da plataforma",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista os usuários com system_role SUPER_ADMIN ou ADMIN_USER, incluindo o nível de acesso " +
            "administrativo (admin_access_level_id) atribuído a cada um, quando houver. Suporta filtros " +
            "combináveis de busca textual (nome/e-mail), status ativo/inativo e nível de acesso. Requer a " +
            "permissão granular 'admin.users.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista de usuários administrativos que atendem aos filtros informados.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.users.view'.")
    public Response listAdminUsers(
            @Parameter(description = "Filtro de busca textual por nome completo ou e-mail (opcional).")
            @QueryParam("search") String search,
            @Parameter(description = "Filtro de status: 'true' para ativos, 'false' para inativos (opcional; interpretado via Boolean.parseBoolean).")
            @QueryParam("status") String status,
            @Parameter(description = "Filtra por ID (UUID) do nível de acesso administrativo atribuído. Opcional.")
            @QueryParam("access_level_id") String accessLevelId) {

        adminAuth.requireAdminPermission("admin.users.view");

        List<AdminUserDTO> users = usersNegocio.list(search, status, accessLevelId);
        return Response.ok(users).build();
    }

    // ─── Criar usuário administrativo ─────────────────────────────────────────

    @POST
    @Operation(
        summary = "Cria um novo usuário administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Cria um usuário administrativo (system_role = ADMIN_USER) a partir de 'email' e " +
            "'fullName' obrigatórios, com 'accessLevelId' opcional (deve referenciar um nível de acesso " +
            "ACTIVE) e 'tempPassword' opcional (gerada automaticamente quando ausente). Um mesmo e-mail não " +
            "pode pertencer simultaneamente à área administrativa e à área cliente — se o e-mail já existir " +
            "como SUPER_ADMIN, ADMIN_USER ou cliente comum, a criação é recusada. Quando " +
            "SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY estão configurados, o usuário também é criado via Admin " +
            "API do Supabase; caso contrário, e se já existir um user_profile para o e-mail, esse registro é " +
            "promovido a ADMIN_USER. Por padrão ('sendPasswordEmail' != false), envia a senha temporária por " +
            "e-mail. Requer a permissão granular 'admin.users.create'."
    )
    @APIResponse(responseCode = "201", description = "Usuário administrativo criado com sucesso; retorna os dados do usuário, a senha temporária e se o e-mail foi enviado.")
    @APIResponse(responseCode = "400", description = "'email'/'fullName' ausentes, nível de acesso inválido/inativo, ou não foi possível criar o usuário (Supabase não configurado e usuário inexistente).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.users.create'.")
    @APIResponse(responseCode = "409", description = "Já existe um usuário (SUPER_ADMIN, ADMIN_USER ou cliente) cadastrado com o e-mail informado.")
    @APIResponse(responseCode = "500", description = "Falha ao criar o usuário no sistema de autenticação (Supabase), ou usuário criado mas não localizado em seguida.")
    public Response createAdminUser(CreateAdminUserRequest req) {
        adminAuth.requireAdminPermission("admin.users.create");

        String email = req.email();
        String fullName = req.fullName();
        String accessLevelId = req.accessLevelId();
        String tempPassword = req.tempPassword();
        boolean sendPasswordEmail = !Boolean.FALSE.equals(req.sendPasswordEmail());

        if (email == null || email.isBlank())
            return Response.status(400).entity(Map.of("error", "email é obrigatório")).build();
        if (fullName == null || fullName.isBlank())
            return Response.status(400).entity(Map.of("error", "fullName é obrigatório")).build();

        if (accessLevelId != null && !accessLevelId.isBlank() && !usersNegocio.isAccessLevelActive(accessLevelId))
            return Response.status(400).entity(Map.of("error", "Nível de acesso não encontrado ou inativo")).build();

        String normalizedEmail = email.trim().toLowerCase();
        Optional<ExistingUserTO> existing = usersNegocio.findExistingByEmail(normalizedEmail);

        if (existing.isPresent()) {
            String existingRole = existing.get().systemRole();
            if ("SUPER_ADMIN".equals(existingRole))
                return Response.status(409).entity(Map.of("error", "Este e-mail pertence ao SUPER_ADMIN")).build();
            if ("ADMIN_USER".equals(existingRole))
                return Response.status(409).entity(Map.of("error", "Já existe um usuário administrativo com este e-mail")).build();
            return Response.status(409).entity(Map.of("error",
                "Este e-mail já está cadastrado como usuário cliente. " +
                "Um e-mail não pode pertencer à área administrativa e à área cliente ao mesmo tempo."
            )).build();
        }

        String effectivePassword = (tempPassword != null && !tempPassword.isBlank())
                ? tempPassword.trim()
                : usersNegocio.generateTempPassword();

        String newUserId = null;
        if (usersNegocio.isSupabaseConfigured()) {
            try {
                newUserId = usersNegocio.createSupabaseUser(normalizedEmail, fullName.trim(), effectivePassword);
            } catch (Exception e) {
                return Response.status(500).entity(Map.of("error", "Erro ao criar usuário no sistema de autenticação: " + e.getMessage())).build();
            }
        }

        if (newUserId == null && existing.isPresent()) {
            newUserId = existing.get().id();
        }

        if (newUserId == null) {
            return Response.status(400).entity(Map.of(
                "error", "Não foi possível criar o usuário. Configure SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY, ou crie o usuário no Supabase primeiro."
            )).build();
        }

        String finalAccessLevelId = (accessLevelId != null && !accessLevelId.isBlank()) ? accessLevelId : null;

        Optional<AdminUserCreatedDTO> created = usersNegocio.finalizeCreate(
            newUserId, fullName.trim(), finalAccessLevelId, normalizedEmail,
            effectivePassword, sendPasswordEmail, adminAuth.currentUserId());

        if (created.isEmpty())
            return Response.status(500).entity(Map.of("error", "Usuário criado mas não encontrado")).build();

        return Response.status(201).entity(created.get()).build();
    }

    // ─── Editar usuário administrativo ────────────────────────────────────────

    @PUT
    @Path("/{id}")
    @Operation(
        summary = "Edita nome e nível de acesso de um usuário administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Atualiza 'fullName' (obrigatório) e 'accessLevelId' (opcional; quando ausente/em branco, " +
            "remove o nível de acesso do usuário) do usuário administrativo identificado por 'id'. Só afeta " +
            "usuários com system_role = ADMIN_USER — o SUPER_ADMIN não pode ser editado por esta rota. Requer " +
            "a permissão granular 'admin.users.edit'."
    )
    @APIResponse(responseCode = "200", description = "Usuário administrativo atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "'fullName' ausente, ou 'accessLevelId' não encontrado/inativo.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, não possui a permissão 'admin.users.edit', ou o alvo é o SUPER_ADMIN (não editável por esta interface).")
    @APIResponse(responseCode = "404", description = "Nenhum usuário administrativo (ADMIN_USER) encontrado para o 'id' informado.")
    public Response updateAdminUser(
            @Parameter(description = "Identificador (UUID) do usuário administrativo.", required = true)
            @PathParam("id") String id,
            UpdateAdminUserRequest req) {
        adminAuth.requireAdminPermission("admin.users.edit");

        String fullName = req.fullName();
        String accessLevelId = req.accessLevelId();

        if (fullName == null || fullName.isBlank())
            return Response.status(400).entity(Map.of("error", "fullName é obrigatório")).build();

        if (usersNegocio.isSuperAdmin(id))
            return Response.status(403).entity(Map.of("error", "Não é permitido editar o SUPER_ADMIN por esta interface")).build();

        int updated;
        if (accessLevelId != null && !accessLevelId.isBlank()) {
            if (!usersNegocio.isAccessLevelActive(accessLevelId))
                return Response.status(400).entity(Map.of("error", "Nível de acesso não encontrado ou inativo")).build();
            updated = usersNegocio.updateProfile(id, fullName.trim(), accessLevelId, adminAuth.currentUserId());
        } else {
            updated = usersNegocio.updateProfile(id, fullName.trim(), null, adminAuth.currentUserId());
        }

        if (updated == 0) return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();
        return Response.ok(Map.of("ok", true)).build();
    }

    // ─── Ativar / Inativar ────────────────────────────────────────────────────

    @PATCH
    @Path("/{id}/status")
    @Operation(
        summary = "Ativa ou inativa um usuário administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Atualiza o campo is_active do usuário administrativo (ADMIN_USER) identificado por " +
            "'id', a partir do booleano obrigatório 'isActive' no corpo. O SUPER_ADMIN nunca pode ser " +
            "inativado por esta rota, e o próprio usuário autenticado não pode inativar a si mesmo. Requer a " +
            "permissão granular 'admin.users.activate'."
    )
    @APIResponse(responseCode = "200", description = "Status atualizado com sucesso; retorna o novo valor de isActive.")
    @APIResponse(responseCode = "400", description = "Campo 'isActive' ausente no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, não possui a permissão 'admin.users.activate', o alvo é o SUPER_ADMIN, ou o usuário está tentando inativar a própria conta.")
    @APIResponse(responseCode = "404", description = "Nenhum usuário administrativo (ADMIN_USER) encontrado para o 'id' informado.")
    public Response updateStatus(
            @Parameter(description = "Identificador (UUID) do usuário administrativo.", required = true)
            @PathParam("id") String id,
            AdminUserStatusRequest req) {
        adminAuth.requireAdminPermission("admin.users.activate");

        Boolean isActive = req != null ? req.isActive() : null;
        if (isActive == null)
            return Response.status(400).entity(Map.of("error", "isActive é obrigatório (true/false)")).build();

        if (usersNegocio.isSuperAdmin(id))
            return Response.status(403).entity(Map.of("error", "Não é permitido inativar o SUPER_ADMIN")).build();

        String currentUserId = adminAuth.currentUserId();
        if (id.equals(currentUserId) && Boolean.FALSE.equals(isActive))
            return Response.status(403).entity(Map.of("error", "Você não pode inativar sua própria conta")).build();

        int updated = usersNegocio.updateActiveStatus(id, isActive, currentUserId);
        if (updated == 0) return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();

        return Response.ok(Map.of("ok", true, "isActive", isActive)).build();
    }

    // ─── Reset de senha ───────────────────────────────────────────────────────

    @POST
    @Path("/{id}/reset-password")
    @Operation(
        summary = "Reseta a senha de um usuário administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Gera uma nova senha temporária e a atualiza no Supabase Auth via Admin API para o " +
            "usuário identificado por 'id'. Requer SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY configurados no " +
            "backend; sem essa configuração, a operação falha. O SUPER_ADMIN não pode ter a senha resetada por " +
            "esta rota. Por padrão ('sendPasswordEmail' != false), envia a nova senha por e-mail ao usuário. " +
            "Requer a permissão granular 'admin.users.reset_password'."
    )
    @APIResponse(responseCode = "200", description = "Senha resetada com sucesso; retorna a senha temporária, o e-mail do usuário e se o e-mail de notificação foi enviado.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, não possui a permissão 'admin.users.reset_password', ou o alvo é o SUPER_ADMIN.")
    @APIResponse(responseCode = "404", description = "Nenhum usuário encontrado para o 'id' informado.")
    @APIResponse(responseCode = "500", description = "SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY não configurados, ou falha ao atualizar a senha no Supabase.")
    public Response resetPassword(
            @Parameter(description = "Identificador (UUID) do usuário administrativo.", required = true)
            @PathParam("id") String id,
            ResetPasswordRequest req) {
        adminAuth.requireAdminPermission("admin.users.reset_password");

        boolean sendPasswordEmail = !Boolean.FALSE.equals(req.sendPasswordEmail());

        Optional<EmailRoleTO> found = usersNegocio.findEmailAndRole(id);
        if (found.isEmpty())
            return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();

        String userEmail  = found.get().email();
        String systemRole = found.get().systemRole();

        if ("SUPER_ADMIN".equals(systemRole))
            return Response.status(403).entity(Map.of("error", "Não é permitido resetar a senha do SUPER_ADMIN")).build();

        boolean supabaseConfigured = usersNegocio.isSupabaseConfigured();
        LOG.debugf("reset-password: userId=%s supabaseConfigured=%s", id, supabaseConfigured);

        if (!supabaseConfigured) {
            return Response.status(500).entity(Map.of(
                "error", "Reset de senha requer VITE_SUPABASE_URL e VITE_SUPABASE_SERVICE_ROLE_KEY configurados no backend."
            )).build();
        }

        String newPassword = usersNegocio.generateTempPassword();

        try {
            usersNegocio.resetSupabasePassword(id, newPassword);
            LOG.infof("reset-password: senha atualizada no Supabase para userId=%s", id);
        } catch (Exception e) {
            LOG.errorf("reset-password: falha ao atualizar senha no Supabase para userId=%s — %s", id, e.getMessage());
            return Response.status(500).entity(Map.of("error", "Erro ao atualizar senha: " + e.getMessage())).build();
        }

        boolean emailSent = usersNegocio.sendResetEmailAndAudit(userEmail, newPassword, sendPasswordEmail, id, adminAuth.currentUserId());

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
    @Operation(
        summary = "Reenvia uma senha (de criação ou de reset) por e-mail a um usuário administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Envia a senha informada em 'password' (obrigatória) por e-mail ao usuário administrativo " +
            "(SUPER_ADMIN ou ADMIN_USER) identificado por 'id'. O template usado depende de 'context': " +
            "'reset' usa o e-mail de redefinição de senha, qualquer outro valor (padrão 'created') usa o " +
            "e-mail de criação de conta. Autorização: é permitido a quem possui a permissão " +
            "'admin.users.create' OU, alternativamente, a permissão 'admin.users.reset_password'."
    )
    @APIResponse(responseCode = "200", description = "E-mail enviado com sucesso; retorna o e-mail do destinatário.")
    @APIResponse(responseCode = "400", description = "Campo 'password' ausente no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui nenhuma das permissões 'admin.users.create'/'admin.users.reset_password'.")
    @APIResponse(responseCode = "404", description = "Nenhum usuário administrativo (SUPER_ADMIN ou ADMIN_USER) encontrado para o 'id' informado.")
    @APIResponse(responseCode = "500", description = "Falha ao enviar o e-mail.")
    public Response sendPasswordEmailEndpoint(
            @Parameter(description = "Identificador (UUID) do usuário administrativo destinatário.", required = true)
            @PathParam("id") String id,
            SendPasswordEmailRequest req) {
        // Permite quem pode criar ou quem pode resetar senha
        boolean allowed = false;
        try { adminAuth.requireAdminPermission("admin.users.create"); allowed = true; } catch (ForbiddenException ignored) {}
        if (!allowed) adminAuth.requireAdminPermission("admin.users.reset_password");

        String password = req.password();
        if (password == null || password.isBlank())
            return Response.status(400).entity(Map.of("error", "password é obrigatório")).build();

        String context = req.context() != null ? req.context() : "created";

        Optional<String> userEmail = usersNegocio.findAdminEmail(id);
        if (userEmail.isEmpty())
            return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();

        boolean sent = usersNegocio.sendPasswordEmail(userEmail.get(), password, context);
        if (!sent)
            return Response.status(500).entity(Map.of("error", "Falha ao enviar e-mail")).build();

        return Response.ok(Map.of("ok", true, "email", userEmail.get())).build();
    }
}
