package de.elbracht.smp.operations;

public enum OperationType {
    START, STOP, RESTART, WHITELIST_ADD, WHITELIST_REMOVE, OP_GRANT, OP_REVOKE, CONSOLE, BACKUP, RESTORE_TEST, CONFIG_APPLY, RESTORE_APPLY, MOD_APPLY;
    public boolean operatorOperation() { return this == OP_GRANT || this == OP_REVOKE; }
    public boolean playerOperation() { return this == WHITELIST_ADD || this == WHITELIST_REMOVE || operatorOperation(); }
}
