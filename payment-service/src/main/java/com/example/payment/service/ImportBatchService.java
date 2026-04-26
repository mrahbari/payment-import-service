package com.example.payment.service;

import com.example.payment.dto.ImportBatchCompleteRequest;
import com.example.payment.dto.ImportBatchResponse;

public interface ImportBatchService {
    ImportBatchResponse start(String fileSha256);
    ImportBatchResponse complete(String fileSha256, ImportBatchCompleteRequest request);
}
