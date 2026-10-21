package com.drtx.bank.payments.iso20022;

import java.math.BigDecimal;
import java.util.List;

/** Customer Credit Transfer Initiation (pain.001.001.09): what a corporate sends to its bank. */
public record Pain001(String messageId, String initiatingParty, int numberOfTransactions, BigDecimal controlSum,
                      List<Instruction> instructions) {

    public record Instruction(String paymentInfoId, String debtorAccount, List<CreditTransfer> transfers) {
    }

    public record CreditTransfer(String endToEndId, BigDecimal amount, String currency, String creditorName,
                                 String creditorAccount, String purpose, String remittance) {
    }
}
