package com.drtx.bank.payments.iso20022;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;

/**
 * Parses pain.001 (any 001.001.xx version: elements are matched by local name) and enforces the
 * group-header integrity rules: NbOfTxs and CtrlSum must match the transactions actually present.
 */
public final class Pain001Parser {

    public static final int MAX_TRANSACTIONS = 500;
    private static final String NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:pain.001.";

    private Pain001Parser() {
    }

    public static Pain001 parse(byte[] xml) {
        Document doc = SecureXml.parse(xml);
        Element root = doc.getDocumentElement();
        if (!"Document".equals(root.getLocalName()) || root.getNamespaceURI() == null
                || !root.getNamespaceURI().startsWith(NAMESPACE_PREFIX)) {
            throw invalid("Not a pain.001 document");
        }
        Element initiation = required(root, "CstmrCdtTrfInitn");
        Element header = required(initiation, "GrpHdr");
        String messageId = text(header, "MsgId", 35);
        int declaredCount = parseInt(text(header, "NbOfTxs", 15));
        String declaredSum = optionalText(header, "CtrlSum");
        String initiator = optionalText(required(header, "InitgPty"), "Nm");

        List<Pain001.Instruction> instructions = new ArrayList<>();
        Set<String> endToEndIds = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Element pmtInf : children(initiation, "PmtInf")) {
            String method = optionalText(pmtInf, "PmtMtd");
            if (method != null && !"TRF".equals(method)) {
                throw invalid("Only PmtMtd=TRF is supported");
            }
            String debtor = account(required(pmtInf, "DbtrAcct"));
            List<Pain001.CreditTransfer> txs = new ArrayList<>();
            for (Element tx : children(pmtInf, "CdtTrfTxInf")) {
                String e2e = text(required(tx, "PmtId"), "EndToEndId", 35);
                if (!endToEndIds.add(e2e)) {
                    throw invalid("Duplicate EndToEndId " + e2e);
                }
                Element instructed = required(required(tx, "Amt"), "InstdAmt");
                BigDecimal amount = amount(instructed.getTextContent());
                String currency = instructed.getAttribute("Ccy");
                if (!currency.matches("[A-Z]{3}")) {
                    throw invalid("Invalid currency on " + e2e);
                }
                Element creditor = child(tx, "Cdtr");
                Element purpose = child(tx, "Purp");
                Element remittance = child(tx, "RmtInf");
                txs.add(new Pain001.CreditTransfer(e2e, amount, currency,
                        creditor == null ? null : optionalText(creditor, "Nm"), account(required(tx, "CdtrAcct")),
                        purpose == null ? null : optionalText(purpose, "Cd"),
                        remittance == null ? null : truncate(optionalText(remittance, "Ustrd"), 140)));
                sum = sum.add(amount);
                count++;
            }
            if (txs.isEmpty()) {
                throw invalid("PmtInf without transactions");
            }
            instructions.add(new Pain001.Instruction(optionalText(pmtInf, "PmtInfId"), debtor, txs));
        }
        if (count == 0 || count > MAX_TRANSACTIONS) {
            throw invalid("A message must contain between 1 and " + MAX_TRANSACTIONS + " transactions");
        }
        if (count != declaredCount) {
            throw invalid("NbOfTxs=" + declaredCount + " but message contains " + count);
        }
        if (declaredSum != null && amount(declaredSum).compareTo(sum) != 0) {
            throw invalid("CtrlSum=" + declaredSum + " but transactions add up to " + sum);
        }
        return new Pain001(messageId, initiator, count, sum, instructions);
    }

    private static String account(Element acct) {
        Element id = required(acct, "Id");
        Element iban = child(id, "IBAN");
        if (iban != null) {
            return iban.getTextContent().trim();
        }
        Element other = required(id, "Othr");
        return text(other, "Id", 34);
    }

    private static BigDecimal amount(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
                throw invalid("Invalid amount " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw invalid("Invalid amount " + raw);
        }
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid("Invalid NbOfTxs " + raw);
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && name.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static Element required(Element parent, String name) {
        Element e = child(parent, name);
        if (e == null) {
            throw invalid("Missing element " + parent.getLocalName() + "/" + name);
        }
        return e;
    }

    private static String text(Element parent, String name, int maxLength) {
        String value = required(parent, name).getTextContent().trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalid("Invalid " + name);
        }
        return value;
    }

    private static String optionalText(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().trim();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.INVALID_MESSAGE, message);
    }
}
