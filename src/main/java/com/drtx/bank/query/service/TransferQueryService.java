package com.drtx.bank.query.service;

import com.drtx.bank.entity.Transfer;
import com.drtx.bank.query.dto.TransferDto;
import com.drtx.bank.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferQueryService {
    private final TransferRepository transferRepository;

    public TransferDto getTransferById(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with ID: " + transferId));

        return mapToDto(transfer);
    }

    public List<TransferDto> getTransfersByFromClient(Long clientId) {
        List<Transfer> transfers = transferRepository.findTransfersByFromClient(clientId);
        return transfers.stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<TransferDto> getTransfersByToClient(Long clientId) {
        List<Transfer> transfers = transferRepository.findTransfersByToClient(clientId);
        return transfers.stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<TransferDto> getAllTransfers() {
        List<Transfer> transfers = transferRepository.findAll();
        return transfers.stream()
                .map(this::mapToDto)
                .toList();
    }

    private TransferDto mapToDto(Transfer transfer) {
        return new TransferDto(
                transfer.getId(),
                transfer.getFromClient().getId(),
                transfer.getToClient().getId(),
                transfer.getAmount(),
                transfer.getTimestamp()
        );
    }
}
