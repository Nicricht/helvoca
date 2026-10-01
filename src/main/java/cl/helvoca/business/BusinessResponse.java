package cl.helvoca.business;
import java.util.UUID;
public record BusinessResponse(UUID id,String name,String timezone,String language,String humanTransferPhone,BusinessStatus status,BusinessMode mode){ public static BusinessResponse from(Business b){return new BusinessResponse(b.getId(),b.getName(),b.getTimezone(),b.getLanguage(),b.getHumanTransferPhone(),b.getStatus(),b.getMode());}}
