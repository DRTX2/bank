package com.drtx.bank.payments.iso20022;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Outbound ISO 20022 messages:
 * <ul>
 *   <li>pain.002.001.10 — Customer Payment Status Report (answer to a pain.001 batch)</li>
 *   <li>pacs.008.001.08 — FI-to-FI Customer Credit Transfer (interbank representation of a transfer)</li>
 *   <li>camt.053.001.08 — Bank-to-Customer Statement (end-of-period account statement)</li>
 * </ul>
 */
public final class Iso20022Messages {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private Iso20022Messages() {
    }

    // ---------------------------------------------------------------- pain.002

    public enum TxStatus { ACSC, ACCP, PDNG, RJCT }

    public record TxReport(String endToEndId, TxStatus status, String reasonCode, String info, BigDecimal amount,
                           String currency, UUID transferId) {
    }

    public record InstructionReport(String paymentInfoId, List<TxReport> transactions) {
    }

    public record StatusReport(String messageId, String originalMessageId, int originalCount, BigDecimal originalSum,
                               String groupStatus, String bic, Instant createdAt, List<InstructionReport> instructions) {
    }

    public static String pain002(StatusReport r) {
        XmlWriter x = new XmlWriter("urn:iso:std:iso:20022:tech:xsd:pain.002.001.10");
        x.open("CstmrPmtStsRpt");
        x.open("GrpHdr").leaf("MsgId", r.messageId()).leaf("CreDtTm", ts(r.createdAt())).agent("DbtrAgt", r.bic()).close();
        x.open("OrgnlGrpInfAndSts")
                .leaf("OrgnlMsgId", r.originalMessageId())
                .leaf("OrgnlMsgNmId", "pain.001.001.09")
                .leaf("OrgnlNbOfTxs", r.originalCount())
                .leaf("OrgnlCtrlSum", r.originalSum().toPlainString())
                .leaf("GrpSts", r.groupStatus())
                .close();
        for (InstructionReport ir : r.instructions()) {
            x.open("OrgnlPmtInfAndSts").leaf("OrgnlPmtInfId", ir.paymentInfoId());
            for (TxReport tx : ir.transactions()) {
                x.open("TxInfAndSts").leaf("OrgnlEndToEndId", tx.endToEndId()).leaf("TxSts", tx.status().name());
                if (tx.reasonCode() != null) {
                    x.open("StsRsnInf").open("Rsn").leaf("Cd", tx.reasonCode()).close().leaf("AddtlInf", tx.info()).close();
                }
                x.open("OrgnlTxRef").open("Amt")
                        .leaf("InstdAmt", tx.amount().toPlainString(), "Ccy", tx.currency())
                        .close().close();
                x.close();
            }
            x.close();
        }
        x.close();
        return x.finish();
    }

    // ---------------------------------------------------------------- pacs.008

    public record CreditTransfer(UUID transferId, String endToEndId, UUID uetr, BigDecimal amount, String currency,
                                 Instant settledAt, String debtorName, String debtorIban, String creditorName,
                                 String creditorIban, String bic, String purpose, String remittance) {
    }

    public static String pacs008(CreditTransfer t) {
        XmlWriter x = new XmlWriter("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08");
        x.open("FIToFICstmrCdtTrf");
        x.open("GrpHdr")
                .leaf("MsgId", "PACS8-" + t.transferId().toString().substring(0, 8).toUpperCase())
                .leaf("CreDtTm", ts(Instant.now()))
                .leaf("NbOfTxs", 1)
                .open("SttlmInf").leaf("SttlmMtd", "INDA").close()
                .close();
        x.open("CdtTrfTxInf");
        x.open("PmtId").leaf("InstrId", t.transferId().toString().replace("-", "").substring(0, 32))
                .leaf("EndToEndId", t.endToEndId()).leaf("UETR", t.uetr()).close();
        x.leaf("IntrBkSttlmAmt", t.amount().toPlainString(), "Ccy", t.currency());
        x.leaf("IntrBkSttlmDt", LocalDate.ofInstant(t.settledAt(), ZoneOffset.UTC));
        x.leaf("ChrgBr", "SLEV");
        x.agent("InstgAgt", t.bic()).agent("InstdAgt", t.bic());
        x.open("Dbtr").leaf("Nm", t.debtorName()).close();
        x.account("DbtrAcct", t.debtorIban());
        x.agent("DbtrAgt", t.bic()).agent("CdtrAgt", t.bic());
        x.open("Cdtr").leaf("Nm", t.creditorName()).close();
        x.account("CdtrAcct", t.creditorIban());
        if (t.purpose() != null) {
            x.open("Purp").leaf("Cd", t.purpose()).close();
        }
        if (t.remittance() != null) {
            x.open("RmtInf").leaf("Ustrd", t.remittance()).close();
        }
        x.close().close();
        return x.finish();
    }

    // ---------------------------------------------------------------- camt.053

    public record StatementEntry(String reference, BigDecimal signedAmount, Instant bookedAt, String bankTxCode,
                                 String endToEndId, UUID uetr, String remittance) {
    }

    public record Statement(String accountIban, String currency, String ownerName, String bic, Instant from, Instant to,
                            BigDecimal openingBalance, BigDecimal closingBalance, List<StatementEntry> entries) {
    }

    public static String camt053(Statement s) {
        Instant now = Instant.now();
        String id = "STMT-" + s.accountIban().substring(s.accountIban().length() - 6) + "-"
                + LocalDate.ofInstant(s.to(), ZoneOffset.UTC).toString().replace("-", "");
        XmlWriter x = new XmlWriter("urn:iso:std:iso:20022:tech:xsd:camt.053.001.08");
        x.open("BkToCstmrStmt");
        x.open("GrpHdr").leaf("MsgId", id).leaf("CreDtTm", ts(now)).close();
        x.open("Stmt").leaf("Id", id).leaf("CreDtTm", ts(now));
        x.open("FrToDt").leaf("FrDtTm", ts(s.from())).leaf("ToDtTm", ts(s.to())).close();
        x.open("Acct").open("Id").leaf("IBAN", s.accountIban()).close().leaf("Ccy", s.currency())
                .open("Ownr").leaf("Nm", s.ownerName()).close().agent("Svcr", s.bic()).close();
        balance(x, "OPBD", s.openingBalance(), s.currency(), s.from());
        balance(x, "CLBD", s.closingBalance(), s.currency(), s.to());
        BigDecimal credits = s.entries().stream().map(StatementEntry::signedAmount).filter(a -> a.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debits = s.entries().stream().map(StatementEntry::signedAmount).filter(a -> a.signum() < 0)
                .map(BigDecimal::negate).reduce(BigDecimal.ZERO, BigDecimal::add);
        long creditCount = s.entries().stream().filter(e -> e.signedAmount().signum() > 0).count();
        x.open("TxsSummry")
                .open("TtlNtries").leaf("NbOfNtries", s.entries().size()).leaf("Sum", credits.add(debits).toPlainString()).close()
                .open("TtlCdtNtries").leaf("NbOfNtries", creditCount).leaf("Sum", credits.toPlainString()).close()
                .open("TtlDbtNtries").leaf("NbOfNtries", s.entries().size() - creditCount).leaf("Sum", debits.toPlainString()).close()
                .close();
        for (StatementEntry e : s.entries()) {
            x.open("Ntry").leaf("NtryRef", e.reference())
                    .leaf("Amt", e.signedAmount().abs().toPlainString(), "Ccy", s.currency())
                    .leaf("CdtDbtInd", e.signedAmount().signum() >= 0 ? "CRDT" : "DBIT")
                    .open("Sts").leaf("Cd", "BOOK").close()
                    .open("BookgDt").leaf("DtTm", ts(e.bookedAt())).close()
                    .open("ValDt").leaf("Dt", LocalDate.ofInstant(e.bookedAt(), ZoneOffset.UTC)).close()
                    .leaf("AcctSvcrRef", e.reference())
                    .open("BkTxCd").open("Prtry").leaf("Cd", e.bankTxCode()).close().close();
            x.open("NtryDtls").open("TxDtls");
            if (e.endToEndId() != null) {
                x.open("Refs").leaf("EndToEndId", e.endToEndId()).leaf("UETR", e.uetr()).close();
            }
            if (e.remittance() != null) {
                x.open("RmtInf").leaf("Ustrd", e.remittance()).close();
            }
            x.close().close().close();
        }
        x.close().close();
        return x.finish();
    }

    private static void balance(XmlWriter x, String code, BigDecimal amount, String currency, Instant at) {
        x.open("Bal").open("Tp").open("CdOrPrtry").leaf("Cd", code).close().close()
                .leaf("Amt", amount.abs().toPlainString(), "Ccy", currency)
                .leaf("CdtDbtInd", amount.signum() >= 0 ? "CRDT" : "DBIT")
                .open("Dt").leaf("Dt", LocalDate.ofInstant(at, ZoneOffset.UTC)).close()
                .close();
    }

    private static String ts(Instant instant) {
        return DATE_TIME.format(instant.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }
}
