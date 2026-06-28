package com.yumesumi.finance.bank.enterprise;

public enum EnterpriseRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageMoney() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canInvite() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageRoles() {
        return this == OWNER;
    }

    public boolean canDisband() {
        return this == OWNER;
    }
}
