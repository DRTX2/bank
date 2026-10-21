package com.drtx.bank.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.drtx.bank.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class Iso20022IntegrationTest extends IntegrationTest {

    @Test
    void pain001BatchProducesPerTransactionPain002Report() throws Exception {
        Customer payer = newAndinaCustomer("1000.00");
        Customer good = newAndinaCustomer("0");
        String msgId = "MSG-" + UUID.randomUUID().toString().substring(0, 8);
        String badIban = good.accountNumber().substring(0, 2) + "00" + good.accountNumber().substring(4);
        String xml = pain001(msgId, payer.accountNumber(), "1060.00",
                tx("E2E-OK", "150.00", good.accountNumber()),
                tx("E2E-BADIBAN", "10.00", badIban),
                tx("E2E-NOFUNDS", "900.00", good.accountNumber()));

        mvc.perform(post("/api/v1/payments/pain001").header("Authorization", "Bearer " + payer.token())
                        .contentType(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_JSON).content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupStatus").value("PART"))
                .andExpect(jsonPath("$.instructions[0].transactions[0].status").value("ACSC"))
                .andExpect(jsonPath("$.instructions[0].transactions[1].reasonCode").value("AC01"))
                .andExpect(jsonPath("$.instructions[0].transactions[2].status").value("RJCT"))
                .andExpect(jsonPath("$.instructions[0].transactions[2].reasonCode").value("AM04"));
        assertThat(balance(good.token(), good.accountId())).isEqualByComparingTo("150.00");

        // Same MsgId again = duplicate file
        mvc.perform(post("/api/v1/payments/pain001").header("Authorization", "Bearer " + payer.token())
                        .contentType(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isoReason").value("DUPL"));
    }

    @Test
    void xmlReportAndStatementsAreValidIsoMessages() throws Exception {
        Customer payer = newAndinaCustomer("300.00");
        Customer payee = newAndinaCustomer("0");
        String xml = pain001("MSG-" + UUID.randomUUID().toString().substring(0, 8), payer.accountNumber(), "25.50",
                tx("E2E-1", "25.50", payee.accountNumber()));

        String report = mvc.perform(post("/api/v1/payments/pain001").header("Authorization", "Bearer " + payer.token())
                        .contentType(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(report).contains("urn:iso:std:iso:20022:tech:xsd:pain.002.001.10", "<GrpSts>ACSC</GrpSts>",
                "<OrgnlEndToEndId>E2E-1</OrgnlEndToEndId>");

        String statement = mvc.perform(get("/api/v1/accounts/" + payer.accountId() + "/statements/camt053")
                        .header("Authorization", "Bearer " + payer.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(statement).contains("camt.053.001.08", "<Cd>OPBD</Cd>", "<Cd>CLBD</Cd>", "<EndToEndId>E2E-1</EndToEndId>",
                "<CdtDbtInd>DBIT</CdtDbtInd>", payer.accountNumber());

        String transfers = mvc.perform(get("/api/v1/transfers").header("Authorization", "Bearer " + payer.token()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(transfers, "$[0].id");
        String pacs = mvc.perform(get("/api/v1/transfers/" + id + "/pacs008").header("Authorization", "Bearer " + payer.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(pacs).contains("pacs.008.001.08", "<UETR>", "<IntrBkSttlmAmt Ccy=\"USD\">25.50</IntrBkSttlmAmt>");
    }

    @Test
    void retailCustomersCannotUploadBatches() throws Exception {
        mvc.perform(post("/api/v1/payments/pain001").header("Authorization", "Bearer " + andina("luis"))
                        .contentType(MediaType.APPLICATION_XML).content("<Document/>"))
                .andExpect(status().isForbidden());
    }

    static String tx(String e2e, String amount, String creditor) {
        return """
                <CdtTrfTxInf>
                  <PmtId><EndToEndId>%s</EndToEndId></PmtId>
                  <Amt><InstdAmt Ccy="USD">%s</InstdAmt></Amt>
                  <Cdtr><Nm>Proveedor</Nm></Cdtr>
                  <CdtrAcct><Id><IBAN>%s</IBAN></Id></CdtrAcct>
                  <RmtInf><Ustrd>Factura %s</Ustrd></RmtInf>
                </CdtTrfTxInf>""".formatted(e2e, amount, creditor, e2e);
    }

    static String pain001(String msgId, String debtor, String controlSum, String... txs) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>
                    <GrpHdr><MsgId>%s</MsgId><CreDtTm>2026-10-21T10:00:00Z</CreDtTm><NbOfTxs>%d</NbOfTxs>
                      <CtrlSum>%s</CtrlSum><InitgPty><Nm>Empresa Demo</Nm></InitgPty></GrpHdr>
                    <PmtInf><PmtInfId>PMT-1</PmtInfId><PmtMtd>TRF</PmtMtd>
                      <Dbtr><Nm>Empresa Demo</Nm></Dbtr>
                      <DbtrAcct><Id><IBAN>%s</IBAN></Id></DbtrAcct>
                      %s
                    </PmtInf>
                  </CstmrCdtTrfInitn>
                </Document>""".formatted(msgId, txs.length, controlSum, debtor, String.join("\n", txs));
    }
}
