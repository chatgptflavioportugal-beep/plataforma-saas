package com.saas.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

/**
 * Chamadas à Admin API do Supabase Auth usadas por AdminUsersService para
 * criar usuários administrativos e resetar senha. Único ponto do
 * admin-service que fala com essa API — nenhuma regra de negócio aqui, só a
 * mecânica HTTP (o corpo é serializado via Jackson em vez de concatenação de
 * string, evitando JSON malformado/injeção quando email/senha/nome contêm
 * aspas ou barras invertidas).
 */
@ApplicationScoped
public class SupabaseAdminClient {

    private static final Logger LOG = Logger.getLogger(SupabaseAdminClient.class);

    @ConfigProperty(name = "supabase.url")
    Optional<String> supabaseUrl;

    @ConfigProperty(name = "supabase.service-role-key")
    Optional<String> supabaseServiceRoleKey;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isConfigured() {
        return supabaseUrl.isPresent() && !supabaseUrl.get().isBlank()
            && supabaseServiceRoleKey.isPresent() && !supabaseServiceRoleKey.get().isBlank();
    }

    public String createUser(String email, String fullName, String password) {
        try {
            String url = supabaseUrl.orElseThrow();
            String key = supabaseServiceRoleKey.orElseThrow();

            String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password,
                "email_confirm", true,
                "user_metadata", Map.of("full_name", fullName)
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/auth/v1/admin/users"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new RuntimeException("Supabase retornou status " + response.statusCode() + ": " + response.body());
            }

            JsonNode node = objectMapper.readTree(response.body());
            if (!node.hasNonNull("id"))
                throw new RuntimeException("Não foi possível extrair ID do usuário criado");
            return node.get("id").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void updateUserPassword(String userId, String newPassword) {
        try {
            String url = supabaseUrl.orElseThrow();
            String key = supabaseServiceRoleKey.orElseThrow();

            String targetUrl = url + "/auth/v1/admin/users/" + userId;
            LOG.debugf("updateUserPassword: PUT %s", targetUrl);

            String reqBody = objectMapper.writeValueAsString(Map.of("password", newPassword));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .PUT(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            LOG.debugf("updateUserPassword: status=%d body=%s", response.statusCode(), response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Supabase retornou status " + response.statusCode() + ": " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
