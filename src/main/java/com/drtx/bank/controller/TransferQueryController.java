package com.drtx.bank.controller;

import com.drtx.bank.query.dto.TransferDto;
import com.drtx.bank.query.service.TransferQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transfers")
public class TransferQueryController {
    private final TransferQueryService transferQueryService;

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferDto> getTransfer(@PathVariable Long transferId) {
        TransferDto transfer = transferQueryService.getTransferById(transferId);
        return ResponseEntity.ok(transfer);
    }

    @GetMapping("/from/{clientId}")
    public ResponseEntity<List<TransferDto>> getTransfersFrom(@PathVariable Long clientId) {
        List<TransferDto> transfers = transferQueryService.getTransfersByFromClient(clientId);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/to/{clientId}")
    public ResponseEntity<List<TransferDto>> getTransfersTo(@PathVariable Long clientId) {
        List<TransferDto> transfers = transferQueryService.getTransfersByToClient(clientId);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping
    public ResponseEntity<List<TransferDto>> getAllTransfers() {
        List<TransferDto> transfers = transferQueryService.getAllTransfers();
        return ResponseEntity.ok(transfers);
    }
}
