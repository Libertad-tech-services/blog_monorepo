package br.com.libertadfacilites.services;

import br.com.libertadfacilites.dtos.EmailResponseDto;
import br.com.libertadfacilites.dtos.LeadEmailRequestDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
public class MailService {

    private static final long MAX_FILE_SIZE_BYTES = 14L * 1024 * 1024;

    private static final int[] CNPJ_FIRST_DIGIT_WEIGHTS = {
            5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2
    };

    private static final int[] CNPJ_SECOND_DIGIT_WEIGHTS = {
            6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2
    };

    private final TemplateEngine templateEngine;
    private final RestClient brevoClient;

    private final String mailFrom;
    private final String mailFromName;
    private final String commercialTo;

    public MailService(
            TemplateEngine templateEngine,
            @Value("${app.brevo.api-key}")
            String brevoApiKey,
            @Value("${app.brevo.base-url:https://api.brevo.com/v3}")
            String brevoBaseUrl,
            @Value("${app.mail.from}")
            String mailFrom,
            @Value("${app.mail.from-name:Libertad Comercial e Serviços}")
            String mailFromName,
            @Value("${app.mail.commercial-to}")
            String commercialTo
    ) {
        this.templateEngine = templateEngine;
        this.mailFrom = mailFrom;
        this.mailFromName = mailFromName;
        this.commercialTo = commercialTo;

        this.brevoClient = RestClient.builder()
                .baseUrl(brevoBaseUrl)
                .defaultHeader("api-key", brevoApiKey)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public EmailResponseDto sendLeadEmail(LeadEmailRequestDto dto) {
        List<String> validationErrors = validateLeadData(dto);

        if (!validationErrors.isEmpty()) {
            return new EmailResponseDto(
                    false,
                    "Dados inválidos: " + String.join(" ", validationErrors)
            );
        }

        try {
            MultipartFile file = dto.getArquivo();

            if (file != null
                    && !file.isEmpty()
                    && file.getSize() > MAX_FILE_SIZE_BYTES) {

                return new EmailResponseDto(
                        false,
                        "O arquivo excede o limite permitido de 14 MB."
                );
            }

            List<String> tiposServicos =
                    normalizeServices(dto.getTiposServicos());

            String servicosStr = String.join(", ", tiposServicos);

            String html = generateEmailHtml(
                    dto,
                    tiposServicos,
                    servicosStr,
                    file
            );

            List<BrevoRecipient> recipients = getRecipients();

            if (recipients.isEmpty()) {
                log.error(
                        "Nenhum destinatário foi configurado em app.mail.commercial-to"
                );

                return new EmailResponseDto(
                        false,
                        "O destinatário interno do formulário não foi configurado."
                );
            }

            BrevoReplyTo replyTo = buildReplyTo(dto);

            List<BrevoAttachment> attachments =
                    buildAttachments(file);

            BrevoEmailRequest request = new BrevoEmailRequest(
                    new BrevoSender(mailFromName, mailFrom),
                    recipients,
                    buildSubject(dto, servicosStr),
                    html,
                    replyTo,
                    attachments
            );

            BrevoEmailResponse response = brevoClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(BrevoEmailResponse.class);

            if (response == null || !hasText(response.messageId())) {
                log.error(
                        "A Brevo retornou uma resposta sem messageId"
                );

                return new EmailResponseDto(
                        false,
                        "O serviço de e-mail não confirmou o envio."
                );
            }

            log.info(
                    "E-mail de lead enviado pela Brevo. messageId={}",
                    response.messageId()
            );

            return new EmailResponseDto(
                    true,
                    "E-mail enviado com sucesso. ID: "
                            + response.messageId()
            );

        } catch (RestClientResponseException ex) {
            log.error(
                    "Erro da API Brevo. status={}, response={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex
            );

            return new EmailResponseDto(
                    false,
                    "Não foi possível enviar a solicitação por e-mail."
            );

        } catch (Exception ex) {
            log.error(
                    "Erro inesperado ao enviar o e-mail do lead",
                    ex
            );

            return new EmailResponseDto(
                    false,
                    "Ocorreu um erro interno ao enviar a solicitação."
            );
        }
    }

    private String generateEmailHtml(
            LeadEmailRequestDto dto,
            List<String> tiposServicos,
            String servicosStr,
            MultipartFile file
    ) {
        Context context = new Context(
                Locale.forLanguageTag("pt-BR")
        );

        context.setVariable(
                "nomeCliente",
                safe(dto.getNomeCliente())
        );

        context.setVariable(
                "nomeEmpresa",
                safe(dto.getNomeEmpresa())
        );

        context.setVariable(
                "cnpj",
                safe(dto.getCnpj())
        );

        context.setVariable(
                "telefone",
                safe(dto.getTelefone())
        );

        context.setVariable(
                "email",
                safe(dto.getEmail())
        );

        context.setVariable(
                "tiposServicos",
                tiposServicos
        );

        context.setVariable(
                "tiposServicosStr",
                servicosStr
        );

        context.setVariable(
                "quantidade",
                dto.getQuantidade()
        );

        context.setVariable(
                "funcaoNaEmpresa",
                safe(dto.getFuncaoNaEmpresa())
        );

        context.setVariable(
                "colaboradoresNecessarios",
                dto.getColaboradoresNecessarios()
        );

        context.setVariable(
                "temAnexo",
                file != null && !file.isEmpty()
        );

        return templateEngine.process(
                "lead-email",
                context
        );
    }

    private BrevoReplyTo buildReplyTo(
            LeadEmailRequestDto dto
    ) {
        if (!hasText(dto.getEmail())) {
            return null;
        }

        return new BrevoReplyTo(
                dto.getEmail().trim(),
                safe(dto.getNomeCliente())
        );
    }

    private List<BrevoAttachment> buildAttachments(
            MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            return List.of();
        }

        String filename = hasText(file.getOriginalFilename())
                ? sanitizeFilename(file.getOriginalFilename())
                : "anexo";

        String base64Content = Base64.getEncoder()
                .encodeToString(file.getBytes());

        return List.of(
                new BrevoAttachment(
                        filename,
                        base64Content
                )
        );
    }

    private String sanitizeFilename(String filename) {
        return filename
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
    }

    private List<BrevoRecipient> getRecipients() {
        if (!hasText(commercialTo)) {
            return List.of();
        }

        return Arrays.stream(commercialTo.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .map(email -> new BrevoRecipient(email, null))
                .toList();
    }

    private List<String> validateLeadData(
            LeadEmailRequestDto dto
    ) {
        List<String> errors = new ArrayList<>();

        if (dto == null) {
            errors.add(
                    "Dados do formulário não foram enviados."
            );

            return errors;
        }

        if (!hasText(dto.getNomeCliente())) {
            errors.add(
                    "Nome do cliente é obrigatório."
            );
        }

        if (!hasText(dto.getNomeEmpresa())) {
            errors.add(
                    "Nome da empresa é obrigatório."
            );
        }

        if (!isValidCnpj(dto.getCnpj())) {
            errors.add(
                    "CNPJ inválido."
            );
        }

        if (!isValidBrazilianPhone(dto.getTelefone())) {
            errors.add(
                    "Telefone inválido."
            );
        }

        if (!hasText(dto.getFuncaoNaEmpresa())) {
            errors.add(
                    "Cargo ou função é obrigatório."
            );
        }

        if (normalizeServices(dto.getTiposServicos()).isEmpty()) {
            errors.add(
                    "Selecione pelo menos um serviço desejado."
            );
        }

        return errors;
    }

    private String buildSubject(
            LeadEmailRequestDto dto,
            String servicosStr
    ) {
        String nomeCliente = hasText(dto.getNomeCliente())
                ? dto.getNomeCliente().trim()
                : "Cliente não informado";

        String servicos = hasText(servicosStr)
                ? servicosStr
                : "Serviço não informado";

        return "Novo lead: "
                + nomeCliente
                + " - "
                + servicos;
    }

    private List<String> normalizeServices(
            List<String> services
    ) {
        if (services == null) {
            return List.of();
        }

        return services.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(service -> !service.isBlank())
                .distinct()
                .toList();
    }

    private boolean isValidCnpj(String cnpj) {
        String digits = onlyDigits(cnpj);

        if (digits.length() != 14
                || hasAllEqualDigits(digits)) {
            return false;
        }

        int firstDigit = calculateCnpjDigit(
                digits.substring(0, 12),
                CNPJ_FIRST_DIGIT_WEIGHTS
        );

        int secondDigit = calculateCnpjDigit(
                digits.substring(0, 12) + firstDigit,
                CNPJ_SECOND_DIGIT_WEIGHTS
        );

        return Character.getNumericValue(
                digits.charAt(12)
        ) == firstDigit
                && Character.getNumericValue(
                digits.charAt(13)
        ) == secondDigit;
    }

    private int calculateCnpjDigit(
            String digits,
            int[] weights
    ) {
        int sum = 0;

        for (int index = 0; index < weights.length; index++) {
            sum += Character.getNumericValue(
                    digits.charAt(index)
            ) * weights[index];
        }

        int remainder = sum % 11;

        return remainder < 2
                ? 0
                : 11 - remainder;
    }

    private boolean isValidBrazilianPhone(
            String phone
    ) {
        String digits = onlyDigits(phone);

        if (digits.startsWith("55")
                && (digits.length() == 12
                || digits.length() == 13)) {
            digits = digits.substring(2);
        }

        if ((digits.length() != 10
                && digits.length() != 11)
                || hasAllEqualDigits(digits)) {
            return false;
        }

        int ddd = Integer.parseInt(
                digits.substring(0, 2)
        );

        if (ddd < 11 || ddd > 99) {
            return false;
        }

        char firstSubscriberDigit = digits.charAt(2);

        if (digits.length() == 11) {
            return firstSubscriberDigit == '9';
        }

        return firstSubscriberDigit >= '2'
                && firstSubscriberDigit <= '5';
    }

    private String onlyDigits(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\D", "");
    }

    private boolean hasAllEqualDigits(String digits) {
        return digits.chars()
                .distinct()
                .count() == 1;
    }

    private boolean hasText(String value) {
        return value != null
                && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record BrevoEmailRequest(
            BrevoSender sender,
            List<BrevoRecipient> to,
            String subject,
            String htmlContent,
            BrevoReplyTo replyTo,
            List<BrevoAttachment> attachment
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record BrevoAttachment(
            String name,
            String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BrevoEmailResponse(
            String messageId
    ) {
    }
}