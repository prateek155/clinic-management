package com.clinic.billing;

import static com.clinic.billing.BillingDtos.*;

import com.clinic.security.FirebasePrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingService {
    private static final String INVOICE_SELECT = """
            SELECT i.id, i.clinic_id, i.clinic_patient_id, i.appointment_id, i.invoice_number, i.status,
                   i.currency, i.subtotal, i.tax_total, i.total,
                   COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.clinic_id = i.clinic_id
                             AND p.invoice_id = i.id AND p.status = 'RECORDED'), 0) AS paid,
                   i.issued_at, i.due_at, COALESCE(pp.preferred_name, pu.full_name) AS patient_name, cp.patient_code
            FROM invoices i
            JOIN clinic_patients cp ON cp.id = i.clinic_patient_id AND cp.clinic_id = i.clinic_id
            JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
            JOIN users pu ON pu.id = pp.user_id
            """;
    private final JdbcTemplate jdbc;

    public BillingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<InvoiceDto> clinicInvoices(FirebasePrincipal principal, UUID clinicId) {
        UUID userId = userId(principal);
        requireBillingRead(userId, clinicId);
        List<UUID> ids = jdbc.query("SELECT id FROM invoices WHERE clinic_id = ? ORDER BY created_at DESC LIMIT 300",
                (rs, row) -> rs.getObject("id", UUID.class), clinicId);
        return ids.stream().map(id -> invoice(clinicId, id)).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDto clinicInvoice(FirebasePrincipal principal, UUID clinicId, UUID invoiceId) {
        requireBillingRead(userId(principal), clinicId);
        return invoice(clinicId, invoiceId);
    }

    @Transactional
    public InvoiceDto createInvoice(FirebasePrincipal principal, UUID clinicId, CreateInvoiceRequest request) {
        UUID actorId = userId(principal);
        requireBillingWrite(actorId, clinicId);
        if (!request.dueAt().isAfter(OffsetDateTime.now())) throw badRequest("Invoice due time must be in the future");
        requireClinicPatient(clinicId, request.clinicPatientId());
        if (request.appointmentId() != null) {
            Integer linked = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM appointments
                    WHERE id = ? AND clinic_id = ? AND clinic_patient_id = ?
                    """, Integer.class, request.appointmentId(), clinicId, request.clinicPatientId());
            if (linked == null || linked == 0) throw badRequest("Appointment does not belong to this patient and clinic");
        }
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal taxTotal = BigDecimal.ZERO.setScale(2);
        List<LineAmount> amounts = request.items().stream().map(item -> {
            BigDecimal base = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = base.multiply(item.taxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return new LineAmount(base, tax, base.add(tax).setScale(2, RoundingMode.HALF_UP));
        }).toList();
        for (LineAmount amount : amounts) { subtotal = subtotal.add(amount.subtotal()); taxTotal = taxTotal.add(amount.tax()); }
        BigDecimal total = subtotal.add(taxTotal).setScale(2, RoundingMode.HALF_UP);
        String invoiceNumber = "INV-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyMM", Locale.ROOT))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID invoiceId = jdbc.queryForObject("""
                INSERT INTO invoices (clinic_id, clinic_patient_id, appointment_id, invoice_number, status, currency,
                                      subtotal, tax_total, total, issued_at, due_at, created_by_user_id)
                VALUES (?, ?, ?, ?, 'ISSUED', 'INR', ?, ?, ?, now(), ?, ?) RETURNING id
                """, UUID.class, clinicId, request.clinicPatientId(), request.appointmentId(), invoiceNumber,
                subtotal, taxTotal, total, request.dueAt(), actorId);
        for (int i = 0; i < request.items().size(); i++) {
            InvoiceItemRequest item = request.items().get(i);
            LineAmount amount = amounts.get(i);
            jdbc.update("""
                    INSERT INTO invoice_items (clinic_id, invoice_id, description, quantity, unit_price, tax_rate, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, clinicId, invoiceId, item.description().trim(), item.quantity(), item.unitPrice(), item.taxRate(), amount.lineTotal());
        }
        audit(clinicId, actorId, "INVOICE_ISSUED", invoiceId, invoiceNumber);
        notifyPatient(clinicId, request.clinicPatientId(), "Invoice issued",
                "Invoice " + invoiceNumber + " was issued for ₹" + total.toPlainString() + ".");
        return invoice(clinicId, invoiceId);
    }

    @Transactional
    public InvoiceDto recordPayment(FirebasePrincipal principal, UUID clinicId, UUID invoiceId, PaymentRequest request) {
        UUID actorId = userId(principal);
        requireBillingWrite(actorId, clinicId);
        InvoiceBalance invoice = jdbc.query("SELECT status, total FROM invoices WHERE id = ? AND clinic_id = ? FOR UPDATE",
                (rs, row) -> new InvoiceBalance(rs.getString("status"), rs.getBigDecimal("total"), BigDecimal.ZERO),
                invoiceId, clinicId).stream().findFirst().orElseThrow(() -> notFound("Invoice not found"));
        BigDecimal paidSoFar = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE invoice_id = ? AND clinic_id = ? AND status = 'RECORDED'
                """, BigDecimal.class, invoiceId, clinicId);
        invoice = new InvoiceBalance(invoice.status(), invoice.total(), paidSoFar == null ? BigDecimal.ZERO : paidSoFar);
        if (!invoice.status().equals("ISSUED") && !invoice.status().equals("PARTIALLY_PAID")) {
            throw conflict("This invoice does not accept payments");
        }
        BigDecimal balance = invoice.total().subtract(invoice.paid()).setScale(2, RoundingMode.HALF_UP);
        if (request.amount().compareTo(balance) > 0) throw badRequest("Payment amount cannot exceed the outstanding balance");
        jdbc.update("""
                INSERT INTO payments (clinic_id, invoice_id, amount, currency, method, reference, status, received_by_user_id)
                VALUES (?, ?, ?, 'INR', ?, ?, 'RECORDED', ?)
                """, clinicId, invoiceId, request.amount(), request.method(), clean(request.reference()), actorId);
        BigDecimal paid = invoice.paid().add(request.amount());
        String nextStatus = paid.compareTo(invoice.total()) >= 0 ? "PAID" : "PARTIALLY_PAID";
        jdbc.update("UPDATE invoices SET status = ?, updated_at = now() WHERE id = ? AND clinic_id = ?",
                nextStatus, invoiceId, clinicId);
        audit(clinicId, actorId, "PAYMENT_RECORDED", invoiceId, request.method());
        notifyInvoicePatient(clinicId, invoiceId, "Payment recorded",
                "A payment of ₹" + request.amount().toPlainString() + " was recorded for the invoice.");
        return invoice(clinicId, invoiceId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> patientInvoices(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        List<UUID> ids = jdbc.query("""
                SELECT i.id FROM invoices i JOIN clinic_patients cp ON cp.id = i.clinic_patient_id AND cp.clinic_id = i.clinic_id
                JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE pp.user_id = ? ORDER BY i.created_at DESC LIMIT 200
                """, (rs, row) -> rs.getObject("id", UUID.class), userId);
        return ids.stream().map(id -> {
            UUID clinicId = jdbc.queryForObject("SELECT clinic_id FROM invoices WHERE id = ?", UUID.class, id);
            return invoice(clinicId, id);
        }).toList();
    }

    private InvoiceDto invoice(UUID clinicId, UUID invoiceId) {
        InvoiceRow row = jdbc.query(INVOICE_SELECT + " WHERE i.id = ? AND i.clinic_id = ?",
                (rs, index) -> new InvoiceRow(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                        rs.getObject("clinic_patient_id", UUID.class), rs.getObject("appointment_id", UUID.class),
                        rs.getString("invoice_number"), rs.getString("status"), rs.getString("currency"),
                        rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total"), rs.getBigDecimal("total"),
                        rs.getBigDecimal("paid"), rs.getObject("issued_at", OffsetDateTime.class),
                        rs.getObject("due_at", OffsetDateTime.class), rs.getString("patient_name"), rs.getString("patient_code")),
                invoiceId, clinicId).stream().findFirst().orElseThrow(() -> notFound("Invoice not found"));
        List<InvoiceItemDto> items = jdbc.query("""
                SELECT id, description, quantity, unit_price, tax_rate, line_total
                FROM invoice_items WHERE invoice_id = ? AND clinic_id = ? ORDER BY id
                """, (rs, index) -> new InvoiceItemDto(rs.getObject("id", UUID.class), rs.getString("description"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("line_total")), invoiceId, clinicId);
        List<PaymentDto> payments = jdbc.query("""
                SELECT id, amount, currency, method, reference, status, received_at
                FROM payments WHERE invoice_id = ? AND clinic_id = ? ORDER BY received_at DESC
                """, (rs, index) -> new PaymentDto(rs.getObject("id", UUID.class), rs.getBigDecimal("amount"),
                rs.getString("currency"), rs.getString("method"), rs.getString("reference"), rs.getString("status"),
                rs.getObject("received_at", OffsetDateTime.class)), invoiceId, clinicId);
        return new InvoiceDto(row.id(), row.clinicId(), row.clinicPatientId(), row.appointmentId(), row.invoiceNumber(),
                row.status(), row.currency(), row.subtotal(), row.taxTotal(), row.total(), row.paid(),
                row.total().subtract(row.paid()).setScale(2, RoundingMode.HALF_UP), row.issuedAt(), row.dueAt(),
                row.patientName(), row.patientCode(), items, payments);
    }

    private void requireBillingRead(UUID userId, UUID clinicId) {
        if (isDoctor(userId, clinicId)) return;
        UUID memberId = staffMemberId(userId, clinicId);
        if (!hasPermission(memberId, "BILLING_READ") && !hasPermission(memberId, "BILLING_WRITE")) {
            throw new AccessDeniedException("Your staff account does not have billing access");
        }
    }

    private void requireBillingWrite(UUID userId, UUID clinicId) {
        if (isDoctor(userId, clinicId)) return;
        UUID memberId = staffMemberId(userId, clinicId);
        if (!hasPermission(memberId, "BILLING_WRITE")) throw new AccessDeniedException("Your staff account cannot manage billing");
    }

    private boolean isDoctor(UUID userId, UUID clinicId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM clinic_members cm JOIN clinics c ON c.id = cm.clinic_id
                WHERE cm.user_id = ? AND cm.clinic_id = ? AND cm.role = 'DOCTOR' AND cm.active = true AND c.active = true
                """, Integer.class, userId, clinicId);
        return count != null && count > 0;
    }

    private UUID staffMemberId(UUID userId, UUID clinicId) {
        return jdbc.query("""
                SELECT id FROM clinic_members WHERE user_id = ? AND clinic_id = ? AND role = 'STAFF' AND active = true
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, clinicId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("Billing access is not enabled for this clinic"));
    }

    private boolean hasPermission(UUID memberId, String permission) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM staff_permissions WHERE clinic_member_id = ? AND permission = ?",
                Integer.class, memberId, permission);
        return count != null && count > 0;
    }

    private void requireClinicPatient(UUID clinicId, UUID clinicPatientId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_patients WHERE id = ? AND clinic_id = ? AND active = true",
                Integer.class, clinicPatientId, clinicId);
        if (count == null || count == 0) throw notFound("Patient not found in this clinic");
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try { return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid()); }
        catch (EmptyResultDataAccessException exception) { throw new AccessDeniedException("Active account not found"); }
    }

    private void audit(UUID clinicId, UUID actorId, String action, UUID invoiceId, String detail) {
        jdbc.update("""
                INSERT INTO audit_logs (clinic_id, actor_user_id, action, entity_type, entity_id, after_data)
                VALUES (?, ?, ?, 'invoice', ?, jsonb_build_object('detail', ?))
                """, clinicId, actorId, action, invoiceId, detail);
    }

    private void notifyPatient(UUID clinicId, UUID clinicPatientId, String title, String body) {
        UUID patientUser = jdbc.queryForObject("""
                SELECT pp.user_id FROM clinic_patients cp JOIN patient_profiles pp ON pp.id = cp.patient_profile_id
                WHERE cp.id = ? AND cp.clinic_id = ?
                """, UUID.class, clinicPatientId, clinicId);
        jdbc.update("INSERT INTO notifications (clinic_id, user_id, category, title, body) VALUES (?, ?, 'BILLING', ?, ?)",
                clinicId, patientUser, title, body);
    }

    private void notifyInvoicePatient(UUID clinicId, UUID invoiceId, String title, String body) {
        UUID patientId = jdbc.queryForObject("SELECT clinic_patient_id FROM invoices WHERE id = ? AND clinic_id = ?",
                UUID.class, invoiceId, clinicId);
        notifyPatient(clinicId, patientId, title, body);
    }

    private String clean(String value) { if (value == null) return null; String text = value.trim(); return text.isEmpty() ? null : text; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    private record LineAmount(BigDecimal subtotal, BigDecimal tax, BigDecimal lineTotal) {}
    private record InvoiceBalance(String status, BigDecimal total, BigDecimal paid) {}
    private record InvoiceRow(UUID id, UUID clinicId, UUID clinicPatientId, UUID appointmentId,
            String invoiceNumber, String status, String currency, BigDecimal subtotal, BigDecimal taxTotal,
            BigDecimal total, BigDecimal paid, OffsetDateTime issuedAt, OffsetDateTime dueAt,
            String patientName, String patientCode) {}
}
