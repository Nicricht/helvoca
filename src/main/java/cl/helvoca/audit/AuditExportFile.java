package cl.helvoca.audit;

public record AuditExportFile(
        String filename,
        String contentType,
        byte[] content
) {}
