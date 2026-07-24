package br.com.libertadfacilites.services;

import br.com.libertadfacilites.dtos.OuvidoriaRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OuvidoriaMailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final TemplateEngine templateEngine;
    private final RestClient brevoClient;

    private final String ouvidoriaTo;
    private final String mailFrom;
    private final String mailFromName;

    public OuvidoriaMailService(
            TemplateEngine templateEngine,

            @Value("${app.brevo.api-key}")
            String brevoApiKey,

            @Value("${app.brevo.base-url:https://api.brevo.com/v3}")
            String brevoBaseUrl,

            @Value("${app.mail.ouvidoria-to}")
            String ouvidoriaTo,

            @Value("${app.mail.from}")
            String mailFrom,

            @Value("${app.mail.from-name:Libertad Comercial e Serviços}")
            String mailFromName
    ) {
        this.templateEngine = templateEngine;
        this.ouvidoriaTo = ouvidoriaTo;
        this.mailFrom = mailFrom;
        this.mailFromName = mailFromName;

        this.brevoClient = RestClient.builder()
                .baseUrl(brevoBaseUrl)
                .defaultHeader("api-key", brevoApiKey)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public void enviarManifestacao(OuvidoriaRequest request) {
        validarRequest(request);
        validarIdentificacao(request);

        boolean identificada = isIdentificada(request);

        Context context = criarContexto(
                request,
                identificada
        );

        String html = templateEngine.process(
                "ouvidoria-template",
                context
        );

        enviarEmailPelaBrevo(
                request,
                html,
                identificada
        );
    }

    private Context criarContexto(
            OuvidoriaRequest request,
            boolean identificada
    ) {
        Context context = new Context(
                Locale.forLanguageTag("pt-BR")
        );

        context.setVariable(
                "tipoManifestacao",
                labelTipoManifestacao(request.tipoManifestacao())
        );

        context.setVariable(
                "identificacao",
                identificada ? "Identificada" : "Anônima"
        );

        context.setVariable(
                "identificada",
                identificada
        );

        /*
         * A anonimização precisa acontecer no backend.
         * Não confie apenas no front-end para remover esses dados.
         */
        context.setVariable(
                "nome",
                identificada
                        ? valueOrDefault(request.nome(), "Não informado")
                        : "Não informado"
        );

        context.setVariable(
                "email",
                identificada
                        ? valueOrDefault(request.email(), "Não informado")
                        : "Não informado"
        );

        context.setVariable(
                "telefone",
                identificada
                        ? valueOrDefault(request.telefone(), "Não informado")
                        : "Não informado"
        );

        context.setVariable(
                "vinculo",
                labelVinculo(request.vinculo())
        );

        context.setVariable(
                "departamento",
                valueOrDefault(
                        request.departamento(),
                        "Não informado"
                )
        );

        context.setVariable(
                "assunto",
                valueOrDefault(
                        request.assunto(),
                        "Sem assunto"
                )
        );

        context.setVariable(
                "descricao",
                valueOrDefault(
                        request.descricao(),
                        "Não informado"
                )
        );

        context.setVariable(
                "dataOcorrencia",
                valueOrDefault(
                        request.dataOcorrencia(),
                        "Não informada"
                )
        );

        context.setVariable(
                "localOcorrencia",
                valueOrDefault(
                        request.localOcorrencia(),
                        "Não informado"
                )
        );

        context.setVariable(
                "envolvidos",
                emptyToNull(request.envolvidos())
        );

        context.setVariable(
                "evidencias",
                emptyToNull(request.evidencias())
        );

        context.setVariable(
                "dataEnvio",
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm"
                        )
                )
        );

        return context;
    }

    private void enviarEmailPelaBrevo(
            OuvidoriaRequest request,
            String html,
            boolean identificada
    ) {
        List<BrevoRecipient> recipients =
                getOuvidoriaRecipients();

        if (recipients.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhum destinatário da Ouvidoria foi configurado."
            );
        }

        BrevoReplyTo replyTo = criarReplyTo(
                request,
                identificada
        );

        BrevoEmailRequest emailRequest =
                new BrevoEmailRequest(
                        new BrevoSender(
                                mailFromName,
                                mailFrom
                        ),
                        recipients,
                        criarAssuntoEmail(request),
                        html,
                        replyTo
                );

        try {
            BrevoEmailResponse response = brevoClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(emailRequest)
                    .retrieve()
                    .body(BrevoEmailResponse.class);

            if (response == null
                    || !isNotBlank(response.messageId())) {

                throw new IllegalStateException(
                        "A Brevo não confirmou o envio do e-mail."
                );
            }
            log.info(
                    "Manifestação enviada à Ouvidoria. messageId={}",
                    response.messageId()
            );

        } catch (RestClientResponseException exception) {
            log.error(
                    "Erro retornado pela Brevo. status={}, response={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );

            throw new IllegalStateException(
                    "Não foi possível enviar a manifestação para a Ouvidoria.",
                    exception
            );

        } catch (Exception exception) {
            log.error(
                    "Erro inesperado ao enviar manifestação à Ouvidoria.",
                    exception
            );

            throw new IllegalStateException(
                    "Erro interno ao enviar a manifestação para a Ouvidoria.",
                    exception
            );
        }
    }

    private BrevoReplyTo criarReplyTo(
            OuvidoriaRequest request,
            boolean identificada
    ) {
        if (!identificada
                || !isEmailValido(request.email())) {
            return null;
        }

        return new BrevoReplyTo(
                request.email().trim(),
                request.nome().trim()
        );
    }

    private List<BrevoRecipient> getOuvidoriaRecipients() {
        if (!isNotBlank(ouvidoriaTo)) {
            return List.of();
        }

        return Arrays.stream(ouvidoriaTo.split(","))
                .map(String::trim)
                .filter(this::isNotBlank)
                .filter(this::isEmailValido)
                .distinct()
                .map(email -> new BrevoRecipient(
                        email,
                        null
                ))
                .toList();
    }

    private String criarAssuntoEmail(
            OuvidoriaRequest request
    ) {
        String tipo = labelTipoManifestacao(
                request.tipoManifestacao()
        );
        return "[Ouvidoria LCS] Nova manifestação - " + tipo;
    }

    private void validarRequest(
            OuvidoriaRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Os dados da manifestação não foram enviados."
            );
        }

        if (!isNotBlank(request.tipoManifestacao())) {
            throw new IllegalArgumentException(
                    "Informe o tipo da manifestação."
            );
        }

        if (!isNotBlank(request.descricao())) {
            throw new IllegalArgumentException(
                    "Informe a descrição da manifestação."
            );
        }

        if (request.descricao().trim().length() > 10_000) {
            throw new IllegalArgumentException(
                    "A descrição deve ter no máximo 10.000 caracteres."
            );
        }
    }

    private void validarIdentificacao(
            OuvidoriaRequest request
    ) {
        if (!isIdentificada(request)) {
            return;
        }

        if (!isNotBlank(request.nome())) {
            throw new IllegalArgumentException(
                    "Informe o nome para manifestação identificada."
            );
        }

        if (!isNotBlank(request.email())) {
            throw new IllegalArgumentException(
                    "Informe o e-mail para manifestação identificada."
            );
        }

        if (!isEmailValido(request.email())) {
            throw new IllegalArgumentException(
                    "Informe um endereço de e-mail válido."
            );
        }
    }

    private boolean isIdentificada(
            OuvidoriaRequest request
    ) {
        return "identificada".equals(
                normalize(request.identificacao())
        );
    }

    private String labelTipoManifestacao(
            String value
    ) {
        return switch (normalize(value)) {
            case "reclamacao" -> "Reclamação";
            case "sugestao" -> "Sugestão";
            case "elogio" -> "Elogio";
            case "duvida" -> "Dúvida";
            case "denuncia" -> "Denúncia ou relato sensível";
            case "outro" -> "Outro";
            default -> valueOrDefault(
                    value,
                    "Não informado"
            );
        };
    }

    private String labelVinculo(
            String value
    ) {
        return switch (normalize(value)) {
            case "colaborador" -> "Colaborador";
            case "cliente" -> "Cliente";
            case "fornecedor" -> "Fornecedor";
            case "parceiro" -> "Parceiro";
            case "candidato" -> "Candidato";
            case "outro" -> "Outro";
            default -> valueOrDefault(
                    value,
                    "Não informado"
            );
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String valueOrDefault(
            String value,
            String defaultValue
    ) {
        if (!isNotBlank(value)) {
            return defaultValue;
        }

        return value.trim();
    }

    private String emptyToNull(String value) {
        if (!isNotBlank(value)) {
            return null;
        }

        return value.trim();
    }

    private boolean isNotBlank(String value) {
        return value != null
                && !value.trim().isEmpty();
    }

    private boolean isEmailValido(String email) {
        return isNotBlank(email)
                && EMAIL_PATTERN.matcher(
                email.trim()
        ).matches();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record BrevoEmailRequest(
            BrevoSender sender,
            List<BrevoRecipient> to,
            String subject,
            String htmlContent,
            BrevoReplyTo replyTo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record BrevoSender(
            String name,
            String email
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record BrevoRecipient(
            String email,
            String name
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record BrevoReplyTo(
            String email,
            String name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BrevoEmailResponse(
            String messageId
    ) {
    }
}