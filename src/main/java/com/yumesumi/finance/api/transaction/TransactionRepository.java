package com.yumesumi.finance.api.transaction;

import com.yumesumi.finance.transaction.TransactionQuery;
import com.yumesumi.finance.transaction.TransactionRecord;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository {
    int MAX_VISIBLE_RECORDS = 1_000;

    void append(TransactionRecord record);

    List<TransactionRecord> find(UUID owner, TransactionQuery query);
}
