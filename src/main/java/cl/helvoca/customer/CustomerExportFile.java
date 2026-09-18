package cl.helvoca.customer;

public record CustomerExportFile(
        String filename,
        String contentType,
        byte[] content
) {}
