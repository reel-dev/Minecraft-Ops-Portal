package de.elbracht.smp.agent;

public class OperationRejected extends RuntimeException {
    final int status;
    final String code;
    OperationRejected(int status, String code) { super(code); this.status = status; this.code = code; }
}
