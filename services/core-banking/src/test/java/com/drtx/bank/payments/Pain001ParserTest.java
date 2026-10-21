package com.drtx.bank.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.drtx.bank.payments.iso20022.Pain001;
import com.drtx.bank.payments.iso20022.Pain001Parser;
import com.drtx.bank.shared.error.DomainException;

import org.junit.jupiter.api.Test;

class Pain001ParserTest {

    private static final String IBAN = "DE89370400440532013000";

    @Test
    void parsesAWellFormedMessage() {
        String xml = Iso20022IntegrationTest.pain001("MSG-1", IBAN, "35.50",
                Iso20022IntegrationTest.tx("A", "10.50", IBAN), Iso20022IntegrationTest.tx("B", "25", IBAN));
        Pain001 msg = Pain001Parser.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(msg.messageId()).isEqualTo("MSG-1");
        assertThat(msg.initiatingParty()).isEqualTo("Empresa Demo");
        assertThat(msg.instructions().getFirst().transfers()).extracting(Pain001.CreditTransfer::endToEndId)
                .containsExactly("A", "B");
        assertThat(msg.controlSum()).isEqualByComparingTo("35.50");
    }

    @Test
    void rejectsControlSumMismatch() {
        String xml = Iso20022IntegrationTest.pain001("MSG-2", IBAN, "99.99", Iso20022IntegrationTest.tx("A", "10", IBAN));
        assertThatThrownBy(() -> Pain001Parser.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DomainException.class).hasMessageContaining("CtrlSum");
    }

    @Test
    void rejectsDuplicateEndToEndIds() {
        String xml = Iso20022IntegrationTest.pain001("MSG-3", IBAN, "20",
                Iso20022IntegrationTest.tx("A", "10", IBAN), Iso20022IntegrationTest.tx("A", "10", IBAN));
        assertThatThrownBy(() -> Pain001Parser.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("Duplicate EndToEndId");
    }

    @Test
    void blocksXxeAttacks() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09"><CstmrCdtTrfInitn>&xxe;</CstmrCdtTrfInitn></Document>
                """;
        assertThatThrownBy(() -> Pain001Parser.parse(xxe.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DomainException.class).hasMessageContaining("unsafe XML");
    }

    @Test
    void rejectsOtherMessageTypes() {
        String camt = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.08\"/>";
        assertThatThrownBy(() -> Pain001Parser.parse(camt.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("Not a pain.001");
    }
}
