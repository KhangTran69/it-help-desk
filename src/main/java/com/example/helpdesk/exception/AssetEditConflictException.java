package com.example.helpdesk.exception;

public class AssetEditConflictException extends RuntimeException {
    public AssetEditConflictException() {
        super("This asset changed after you opened it. Reload the asset and reapply your changes.");
    }
}
