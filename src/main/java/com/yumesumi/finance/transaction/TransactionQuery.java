package com.yumesumi.finance.transaction;

public record TransactionQuery(
        TransactionDirection direction,
        TransactionSortField sortField,
        TransactionSortOrder sortOrder,
        String searchText,
        int limit
) {
    public static TransactionQuery latest() {
        return new TransactionQuery(TransactionDirection.ALL, TransactionSortField.TIME, TransactionSortOrder.DESC, "", 1_000);
    }

    public TransactionQuery {
        direction = direction == null ? TransactionDirection.ALL : direction;
        sortField = sortField == null ? TransactionSortField.TIME : sortField;
        sortOrder = sortOrder == null ? TransactionSortOrder.DESC : sortOrder;
        searchText = searchText == null ? "" : searchText.trim();
        limit = Math.max(1, Math.min(1_000, limit));
    }
}
