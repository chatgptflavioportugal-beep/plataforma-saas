package com.saas.admin.resource;

import com.saas.admin.dto.PlanModuleWithLimitsRequest;
import com.saas.admin.dto.PlanRequest;
import com.saas.admin.dto.PlanVersionModuleLimitRequest;
import com.saas.admin.dto.PlanVersionModuleRequest;
import com.saas.platformadmin.PlatformAdminAuthService;
import com.saas.admin.negocio.impl.AdminPlanNegocio;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CRUD administrativo de planos, versionamento e módulos/limitações de cada
 * versão. Movido de subscription-service (PlanAdminResource) para
 * consolidar em admin-service, único dono de plans/plan_version_modules/
 * plan_version_module_limits.
 */
@Path("/api/v1/admin/plans")
@Tag(name = "Plans", description = "CRUD administrativo de planos de assinatura, versionamento (plans), módulos incluídos em cada versão (plan_version_modules) e suas limitações (plan_version_module_limits). Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPlanResource {

    @Inject AdminPlanNegocio planNegocio;
    @Inject PlatformAdminAuthService adminAuth;

    // ----------------------------------------------------------------
    // Planos
    // ----------------------------------------------------------------

    @GET
    @Operation(
        summary = "Lista todos os planos e versões da plataforma, com indicadores de assinantes",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Lista todas as linhas de plans (todas as " +
            "versões, não só a atual), com contagem de assinantes pagos e em Trial " +
            "(`paid_subscriptions`/`trial_subscriptions`, somados em `subscriber_count`), " +
            "totais de preço mensal/anual calculados a partir dos módulos ativos da versão, e " +
            "`module_count`. Ordenado por tipo de plano, código e versão."
    )
    @APIResponse(responseCode = "200", description = "Lista de planos e versões (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.view`.")
    public Response listPlans() {
        adminAuth.requireAdminPermission("admin.plans.view");
        return Response.ok(planNegocio.listAllPlansAdmin()).build();
    }

    @GET
    @Path("/{code}/versions")
    @Operation(
        summary = "Lista o histórico completo de versões de um plano pelo código",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Retorna todas as versões (`version`) já criadas " +
            "para o `code` do plano, da mais recente para a mais antiga, cada uma com os " +
            "mesmos indicadores de assinantes/preços de `GET /`, mais o detalhamento completo " +
            "dos módulos e limitações daquela versão (`modules_json`) e o panorama de " +
            "campanhas de Trial ativas/canceladas (`trial_campaigns_active`/" +
            "`trial_campaigns_cancelled`). Não valida se o `code` existe — para um código " +
            "inexistente, retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de versões do plano (pode ser vazia se o código não existir).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.version_history`.")
    public Response getPlanVersions(
            @Parameter(description = "Código (`plans.code`) do plano cujo histórico de versões será listado.", required = true) @PathParam("code") String code) {
        adminAuth.requireAdminPermission("admin.plans.version_history");
        return Response.ok(planNegocio.getPlanVersionHistory(code)).build();
    }

    @POST
    @Operation(
        summary = "Cria um novo plano (versão 1, sem plano pai)",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria a primeira versão (`version = 1`, `is_current_version = " +
            "true`, ativo) de um plano com `code` e `name` obrigatórios. Preços mensal/anual " +
            "são sempre criados como zero — os valores reais vêm da soma dos módulos " +
            "adicionados depois via `POST /{planId}/modules`. `max_users` (padrão 5), " +
            "`max_ai_requests_month` (padrão 100), `billing_type` (padrão \"both\") e " +
            "`plan_type` (padrão \"business\") assumem padrões quando omitidos."
    )
    @APIResponse(responseCode = "201", description = "Plano criado com sucesso; retorna `id`, `version = 1` e `created = true`.")
    @APIResponse(responseCode = "400", description = "`code` ou `name` ausentes ou em branco no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.create`.")
    public Response createPlan(Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.create");
        var req = mapToRequest(body);
        if (req.code() == null || req.code().isBlank())
            return Response.status(400).entity(Map.of("error", "code é obrigatório")).build();
        if (req.name() == null || req.name().isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        return Response.status(201).entity(planNegocio.createPlan(req)).build();
    }

    @POST
    @Path("/{id}/new-version")
    @Operation(
        summary = "Cria uma nova versão de um plano, copiando módulos e limitações",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Gera a próxima versão do plano identificado por `id` (que deve " +
            "ser a versão atual — `is_current_version = true`), marcando a versão anterior " +
            "como não-atual e copiando automaticamente todos os módulos e limitações " +
            "(`plan_version_modules`/`plan_version_module_limits`) para a nova versão. Campos " +
            "omitidos no corpo herdam o valor da versão anterior. Como efeito colateral, " +
            "cancela (e audita) todas as campanhas de Trial ACTIVE/SCHEDULED da versão " +
            "antiga, já que elas promoviam especificamente aquela versão."
    )
    @APIResponse(responseCode = "201", description = "Nova versão criada com sucesso; retorna `id`, `version` e `new_version_created = true`.")
    @APIResponse(responseCode = "400", description = "Corpo da requisição inválido para a criação da nova versão.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.create_version`.")
    @APIResponse(responseCode = "404", description = "Nenhum plano encontrado para o `id` informado que seja, ao mesmo tempo, a versão atual.")
    public Response createNewVersion(
            @Parameter(description = "ID (UUID) da versão atual do plano a partir da qual a nova versão será criada.", required = true) @PathParam("id") String id,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.create_version");
        try {
            var req = mapToRequest(body);
            return Response.status(201).entity(planNegocio.createNewVersion(id, req, adminAuth.currentUserId())).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/edit")
    @Operation(
        summary = "Edita um plano criando uma nova versão com o conjunto completo de módulos",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Variante de `POST /{id}/new-version` usada pela tela de edição " +
            "unificada: além dos dados do plano, recebe a lista completa `modules` (cada um " +
            "com preços, status, ordem e suas `limits`) que substitui inteiramente os módulos " +
            "copiados da versão anterior — quando `modules` não é enviado, o comportamento " +
            "cai para a mesma cópia automática de `new-version`. Também cancela (e audita) as " +
            "campanhas de Trial ACTIVE/SCHEDULED da versão antiga."
    )
    @APIResponse(responseCode = "201", description = "Nova versão criada com sucesso, com o conjunto de módulos informado; retorna `id`, `version` e `new_version_created = true`.")
    @APIResponse(responseCode = "400", description = "Corpo da requisição inválido para a criação da nova versão.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum plano encontrado para o `id` informado que seja, ao mesmo tempo, a versão atual.")
    public Response editPlanWithNewVersion(
            @Parameter(description = "ID (UUID) da versão atual do plano a ser editado.", required = true) @PathParam("id") String id,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToRequest(body);
            var modules = mapToPlanModuleWithLimitsRequests(body);
            return Response.status(201).entity(planNegocio.createNewVersionWithModules(id, req, modules, adminAuth.currentUserId())).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Operation(
        summary = "Ativa ou inativa um plano (alterna o status atual)",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Não recebe corpo: inverte diretamente `is_active` do plano. " +
            "Como efeito colateral, se o plano for inativado e estava marcado como \"mais " +
            "popular\" (`is_most_popular`), essa marcação é removida automaticamente."
    )
    @APIResponse(responseCode = "200", description = "Status do plano alternado com sucesso; retorna `id` e `is_active` atualizado.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.activate`.")
    @APIResponse(responseCode = "404", description = "Nenhum plano encontrado para o `id` informado.")
    public Response togglePlanStatus(
            @Parameter(description = "ID (UUID) do plano a ativar/inativar.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.plans.activate");
        try {
            return Response.ok(planNegocio.togglePlanStatus(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}/popular")
    @Consumes(MediaType.WILDCARD)
    @Operation(
        summary = "Marca um plano como \"Mais Popular\" (destaque comercial)",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Não recebe corpo. Só é permitido marcar um plano que esteja " +
            "ativo e que seja a versão atual (`is_current_version = true`). A marcação é " +
            "exclusiva: qualquer outro plano previamente marcado como mais popular é " +
            "desmarcado automaticamente antes de aplicar a nova marcação."
    )
    @APIResponse(responseCode = "200", description = "Plano marcado como mais popular com sucesso; retorna `id` e `is_most_popular = true`.")
    @APIResponse(responseCode = "400", description = "O plano está inativo, ou não é a versão atual do plano.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum plano encontrado para o `id` informado.")
    public Response setMostPopular(
            @Parameter(description = "ID (UUID) do plano a marcar como mais popular.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planNegocio.setMostPopular(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Módulos da versão do plano (plan_version_modules)
    // ----------------------------------------------------------------

    @GET
    @Path("/{planId}/modules")
    @Operation(
        summary = "Lista os módulos incluídos em uma versão de plano, com suas limitações",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Lista os registros de plan_version_modules da " +
            "versão de plano informada, cada um com dados do módulo, preços mensal/anual, " +
            "status e a lista de limitações (`limits_json`, como texto JSON) configuradas " +
            "para aquele módulo naquela versão. Não valida se o plano existe — para um " +
            "`planId` inexistente, retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de módulos da versão do plano (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.view`.")
    public Response listPlanVersionModules(
            @Parameter(description = "ID (UUID) da versão do plano (plans.id) cujos módulos serão listados.", required = true) @PathParam("planId") String planId) {
        adminAuth.requireAdminPermission("admin.plans.view");
        return Response.ok(planNegocio.listPlanVersionModules(planId)).build();
    }

    @POST
    @Path("/{planId}/modules")
    @Operation(
        summary = "Adiciona um módulo a uma versão de plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria um registro em plan_version_modules vinculando o " +
            "`module_id` informado (obrigatório) à versão de plano do path, com " +
            "`monthly_price`/`annual_monthly_price` (padrão zero), `status` (padrão " +
            "\"active\") e `sort_order` (padrão 99). Bloqueado se a versão do plano já " +
            "possuir assinantes (`tenant_subscriptions` em trial/active/past_due) — nesse " +
            "caso é preciso criar uma nova versão do plano para alterar os módulos."
    )
    @APIResponse(responseCode = "201", description = "Módulo adicionado à versão do plano com sucesso; retorna o `id` gerado.")
    @APIResponse(responseCode = "400", description = "`module_id` ausente, o módulo já está adicionado a esta versão do plano, ou a versão já possui assinantes.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum plano encontrado para o `planId` informado.")
    public Response addPlanVersionModule(
            @Parameter(description = "ID (UUID) da versão do plano à qual o módulo será adicionado.", required = true) @PathParam("planId") String planId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleRequest(body);
            return Response.status(201).entity(planNegocio.addPlanVersionModule(planId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{planId}/modules/{pvmId}")
    @Operation(
        summary = "Atualiza preços, status e ordem de um módulo de uma versão de plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Substitui `monthly_price`/`annual_monthly_price` (padrão zero " +
            "quando omitidos), `status` (padrão \"active\") e `sort_order` (padrão 99) do " +
            "módulo identificado por `pvmId`. Bloqueado se a versão do plano já possuir " +
            "assinantes — nesse caso é preciso criar uma nova versão do plano para alterar os " +
            "módulos. O `planId` do path não é usado para restringir a atualização (a " +
            "resolução do plano é feita a partir do próprio `pvmId`)."
    )
    @APIResponse(responseCode = "200", description = "Módulo da versão do plano atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "A versão do plano já possui assinantes e não pode ter seus módulos alterados.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum módulo de plano encontrado para o `pvmId` informado.")
    public Response updatePlanVersionModule(
            @Parameter(description = "ID (UUID) da versão do plano (informativo; a resolução usa `pvmId`).", required = true) @PathParam("planId") String planId,
            @Parameter(description = "ID (UUID) do registro plan_version_modules a atualizar.", required = true) @PathParam("pvmId") String pvmId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleRequest(body);
            return Response.ok(planNegocio.updatePlanVersionModule(pvmId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{planId}/modules/{pvmId}")
    @Operation(
        summary = "Remove um módulo de uma versão de plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Remove definitivamente o registro plan_version_modules " +
            "identificado por `pvmId` (e, por integridade referencial, suas limitações " +
            "associadas). Bloqueado se a versão do plano já possuir assinantes — nesse caso é " +
            "preciso criar uma nova versão do plano para alterar os módulos."
    )
    @APIResponse(responseCode = "200", description = "Módulo removido da versão do plano com sucesso.")
    @APIResponse(responseCode = "400", description = "A versão do plano já possui assinantes e não pode ter seus módulos alterados.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum módulo de plano encontrado para o `pvmId` informado.")
    public Response removePlanVersionModule(
            @Parameter(description = "ID (UUID) da versão do plano (informativo; a resolução usa `pvmId`).", required = true) @PathParam("planId") String planId,
            @Parameter(description = "ID (UUID) do registro plan_version_modules a remover.", required = true) @PathParam("pvmId") String pvmId) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planNegocio.removePlanVersionModule(pvmId)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Limitações dos módulos do plano (plan_version_module_limits)
    // ----------------------------------------------------------------

    @POST
    @Path("/{planId}/modules/{pvmId}/limits")
    @Operation(
        summary = "Adiciona uma limitação a um módulo de uma versão de plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria um registro em plan_version_module_limits vinculado ao " +
            "`pvmId` informado, com `title` obrigatório; `description`, `code`, " +
            "`limit_value`, `unit` e `sort_order` (padrão 99) são opcionais. Diferente das " +
            "operações de módulo, não há checagem de assinantes existentes aqui."
    )
    @APIResponse(responseCode = "201", description = "Limitação criada com sucesso; retorna o `id` gerado.")
    @APIResponse(responseCode = "400", description = "`title` ausente ou em branco no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum módulo de plano encontrado para o `pvmId` informado.")
    public Response addPlanVersionModuleLimit(
            @Parameter(description = "ID (UUID) da versão do plano (informativo; a resolução usa `pvmId`).", required = true) @PathParam("planId") String planId,
            @Parameter(description = "ID (UUID) do registro plan_version_modules ao qual a limitação será adicionada.", required = true) @PathParam("pvmId") String pvmId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleLimitRequest(body);
            return Response.status(201).entity(planNegocio.addPlanVersionModuleLimit(pvmId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{planId}/modules/{pvmId}/limits/{limitId}")
    @Operation(
        summary = "Atualiza uma limitação de um módulo de uma versão de plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Substitui `title` (obrigatório), `description`, `code`, " +
            "`limit_value`, `unit` e `sort_order` (padrão 99) da limitação identificada por " +
            "`limitId`. `planId` e `pvmId` do path não são usados para restringir a " +
            "atualização (a resolução é feita a partir do próprio `limitId`)."
    )
    @APIResponse(responseCode = "200", description = "Limitação atualizada com sucesso.")
    @APIResponse(responseCode = "400", description = "`title` ausente ou em branco no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhuma limitação encontrada para o `limitId` informado.")
    public Response updatePlanVersionModuleLimit(
            @Parameter(description = "ID (UUID) da versão do plano (informativo).", required = true) @PathParam("planId") String planId,
            @Parameter(description = "ID (UUID) do módulo da versão do plano (informativo).", required = true) @PathParam("pvmId") String pvmId,
            @Parameter(description = "ID (UUID) da limitação (plan_version_module_limits) a atualizar.", required = true) @PathParam("limitId") String limitId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleLimitRequest(body);
            return Response.ok(planNegocio.updatePlanVersionModuleLimit(limitId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{planId}/modules/{pvmId}/limits/{limitId}")
    @Operation(
        summary = "Remove uma limitação de um módulo de uma versão de plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Remove definitivamente o registro plan_version_module_limits " +
            "identificado por `limitId`. `planId` e `pvmId` do path não são usados para " +
            "restringir a remoção (a resolução é feita a partir do próprio `limitId`)."
    )
    @APIResponse(responseCode = "200", description = "Limitação removida com sucesso.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.plans.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhuma limitação encontrada para o `limitId` informado.")
    public Response removePlanVersionModuleLimit(
            @Parameter(description = "ID (UUID) da versão do plano (informativo).", required = true) @PathParam("planId") String planId,
            @Parameter(description = "ID (UUID) do módulo da versão do plano (informativo).", required = true) @PathParam("pvmId") String pvmId,
            @Parameter(description = "ID (UUID) da limitação (plan_version_module_limits) a remover.", required = true) @PathParam("limitId") String limitId) {
        adminAuth.requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planNegocio.removePlanVersionModuleLimit(limitId)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private PlanRequest mapToRequest(Map<String, Object> body) {
        return new PlanRequest(
            (String) body.get("name"),
            (String) body.get("code"),
            (String) body.get("description"),
            body.get("price_monthly") != null ? new BigDecimal(body.get("price_monthly").toString()) : null,
            body.get("price_annual")  != null ? new BigDecimal(body.get("price_annual").toString())  : null,
            body.get("discount_annual_percent") != null ? ((Number) body.get("discount_annual_percent")).intValue() : null,
            body.get("max_users") != null ? ((Number) body.get("max_users")).intValue() : null,
            body.get("max_ai_requests_month") != null ? ((Number) body.get("max_ai_requests_month")).intValue() : null,
            (String) body.get("billing_type"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null,
            (String) body.get("plan_type")
        );
    }

    private PlanVersionModuleRequest mapToPlanVersionModuleRequest(Map<String, Object> body) {
        return new PlanVersionModuleRequest(
            (String) body.get("module_id"),
            body.get("monthly_price")         != null ? new BigDecimal(body.get("monthly_price").toString())         : null,
            body.get("annual_monthly_price")  != null ? new BigDecimal(body.get("annual_monthly_price").toString())  : null,
            (String) body.get("status"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }

    private PlanVersionModuleLimitRequest mapToPlanVersionModuleLimitRequest(Map<String, Object> body) {
        return new PlanVersionModuleLimitRequest(
            (String) body.get("title"),
            (String) body.get("description"),
            (String) body.get("code"),
            (String) body.get("limit_value"),
            (String) body.get("unit"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }

    @SuppressWarnings("unchecked")
    private List<PlanModuleWithLimitsRequest> mapToPlanModuleWithLimitsRequests(Map<String, Object> body) {
        Object raw = body.get("modules");
        if (!(raw instanceof List<?> list)) return null;
        return list.stream().map(item -> {
            Map<String, Object> m = (Map<String, Object>) item;
            List<PlanVersionModuleLimitRequest> limits = null;
            if (m.get("limits") instanceof List<?> ll) {
                limits = ll.stream().map(li -> {
                    Map<String, Object> l = (Map<String, Object>) li;
                    return new PlanVersionModuleLimitRequest(
                        (String) l.get("title"),
                        (String) l.get("description"),
                        (String) l.get("code"),
                        (String) l.get("limit_value"),
                        (String) l.get("unit"),
                        l.get("sort_order") != null ? ((Number) l.get("sort_order")).intValue() : null
                    );
                }).collect(java.util.stream.Collectors.toList());
            }
            return new PlanModuleWithLimitsRequest(
                (String) m.get("module_id"),
                m.get("monthly_price")        != null ? new BigDecimal(m.get("monthly_price").toString())        : null,
                m.get("annual_monthly_price") != null ? new BigDecimal(m.get("annual_monthly_price").toString()) : null,
                (String) m.get("status"),
                m.get("sort_order") != null ? ((Number) m.get("sort_order")).intValue() : null,
                limits
            );
        }).collect(java.util.stream.Collectors.toList());
    }
}
