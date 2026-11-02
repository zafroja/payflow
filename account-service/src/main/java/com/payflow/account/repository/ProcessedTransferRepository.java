package com.payflow.account.repository;

import com.payflow.account.domain.ProcessedTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedTransferRepository extends JpaRepository<ProcessedTransfer, String> {
}
