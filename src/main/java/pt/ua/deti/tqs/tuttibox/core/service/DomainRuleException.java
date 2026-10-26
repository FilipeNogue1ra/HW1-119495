package pt.ua.deti.tqs.tuttibox.core.service;

public class DomainRuleException extends RuntimeException {

    private final String errorCode;

    public DomainRuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
