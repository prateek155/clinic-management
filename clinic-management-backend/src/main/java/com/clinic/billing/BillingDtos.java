package com.clinic.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record InvoiceItemRequest(@NotBlank @Size(max = 240) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {}

    public record CreateInvoiceRequest(@NotNull UUID clinicPatientId, UUID appointmentId,
            @NotNull OffsetDateTime dueAt, @NotEmpty @Size(max = 30) List<@Valid InvoiceItemRequest> items) {}

    public record PaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = "CASH|UPI|CARD|BANK_TRANSFER|ONLINE") String method,
            @Size(max = 120) String reference) {}

    public record InvoiceItemDto(UUID id, String description, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal taxRate, BigDecimal lineTotal) {}
    public record PaymentDto(UUID id, BigDecimal amount, String currency, String method, String reference,
            String status, OffsetDateTime receivedAt) {}
    public record InvoiceDto(UUID id, UUID clinicId, UUID clinicPatientId, UUID appointmentId,
            String invoiceNumber, String status, String currency, BigDecimal subtotal, BigDecimal taxTotal,
            BigDecimal total, BigDecimal paid, BigDecimal balance, OffsetDateTime issuedAt, OffsetDateTime dueAt,
            String patientName, String patientCode, List<InvoiceItemDto> items, List<PaymentDto> payments) {}
}
