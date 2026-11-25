package com.clinic.billing;

import static com.clinic.billing.BillingDtos.*;

import com.clinic.security.FirebasePrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) { this.billing = billing; }

    @GetMapping("/billing/invoices")
    public List<InvoiceDto> clinicInvoices(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId) { return billing.clinicInvoices(principal, clinicId); }

    @PostMapping("/billing/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDto createInvoice(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @Valid @RequestBody CreateInvoiceRequest request) {
        return billing.createInvoice(principal, clinicId, request);
    }

    @GetMapping("/billing/invoices/{invoiceId}")
    public InvoiceDto clinicInvoice(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID invoiceId) {
        return billing.clinicInvoice(principal, clinicId, invoiceId);
    }

    @PostMapping("/billing/invoices/{invoiceId}/payments")
    public InvoiceDto recordPayment(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam UUID clinicId, @PathVariable UUID invoiceId,
            @Valid @RequestBody PaymentRequest request) {
        return billing.recordPayment(principal, clinicId, invoiceId, request);
    }

    @GetMapping("/patient/invoices")
    public List<InvoiceDto> patientInvoices(@AuthenticationPrincipal FirebasePrincipal principal) {
        return billing.patientInvoices(principal);
    }
}
