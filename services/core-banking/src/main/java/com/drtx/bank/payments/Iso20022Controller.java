package com.drtx.bank.payments;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.drtx.bank.payments.iso20022.Iso20022Messages;
import com.drtx.bank.payments.iso20022.SecureXml;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.web.Amounts;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class Iso20022Controller {

    private final Iso20022Service iso;

    Iso20022Controller(Iso20022Service iso) {
        this.iso = iso;
    }

    record BatchView(UUID id, String messageId, int numberOfTxs, BigDecimal controlSum, String status, Instant createdAt) {
    }

    /** Upload a pain.001 file; answers with a pain.002 status report (XML) or its JSON equivalent. */
    @PostMapping(path = "/api/v1/payments/pain001", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasAuthority('payments:batch')")
    String uploadXml(CurrentUser user, HttpServletRequest request) throws IOException {
        return Iso20022Messages.pain002(iso.processPain001(user, body(request)));
    }

    @PostMapping(path = "/api/v1/payments/pain001", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('payments:batch')")
    Iso20022Messages.StatusReport uploadJson(CurrentUser user, HttpServletRequest request) throws IOException {
        return iso.processPain001(user, body(request));
    }

    @GetMapping("/api/v1/payments/batches")
    @PreAuthorize("hasAuthority('payments:batch') or hasAuthority('transfers:read:any')")
    List<BatchView> batches(CurrentUser user) {
        return iso.batches(user).stream().map(b -> new BatchView(b.getId(), b.getMessageId(), b.getNumberOfTxs(),
                Amounts.fromMinor(b.getControlSumMinor()), b.getStatus(), b.getCreatedAt())).toList();
    }

    @GetMapping(path = "/api/v1/transfers/{id}/pacs008", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasAnyAuthority('transfers:read:own', 'transfers:read:any')")
    ResponseEntity<String> pacs008(CurrentUser user, @PathVariable UUID id) {
        return attachment("pacs008-" + id + ".xml", iso.pacs008(user, id));
    }

    @GetMapping(path = "/api/v1/accounts/{id}/statements/camt053", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasAnyAuthority('statements:read:own', 'statements:read:any')")
    ResponseEntity<String> camt053(CurrentUser user, @PathVariable UUID id,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        if (end.isBefore(start) || start.plusYears(1).isBefore(end)) {
            throw new DomainException(ErrorCode.VALIDATION, "Statement period must be between 1 day and 1 year");
        }
        String xml = iso.camt053(user, id, start.atStartOfDay().toInstant(ZoneOffset.UTC),
                end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        return attachment("camt053-" + start + "-" + end + ".xml", xml);
    }

    private static byte[] body(HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(SecureXml.MAX_BYTES + 1);
        if (bytes.length == 0) {
            throw new DomainException(ErrorCode.INVALID_MESSAGE, "Empty body");
        }
        return bytes;
    }

    private static ResponseEntity<String> attachment(String filename, String xml) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }
}
