package com.shopmanagement.ledgerservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.ledgerservice.model.BankStatementLine;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Long> {

    List<BankStatementLine> findByBatchIdOrderByLineNoAsc(Long batchId);
}
