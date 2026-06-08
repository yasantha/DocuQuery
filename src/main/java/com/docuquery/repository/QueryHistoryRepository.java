package com.docuquery.repository;

import com.docuquery.entity.QueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    List<QueryHistory> findByDocumentIdOrderByQueriedAtDesc(Long documentId);
}
