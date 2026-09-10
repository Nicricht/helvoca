package cl.helvoca.business;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="business")
public class Business {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,length=150) private String name;
 @Column(nullable=false,length=60) private String timezone="America/Santiago";
 @Column(nullable=false,length=10) private String language="es";
 @Column(name="human_transfer_phone",length=32) private String humanTransferPhone;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private BusinessStatus status=BusinessStatus.ACTIVE;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void prePersist(){var now=Instant.now();createdAt=now;updatedAt=now;} @PreUpdate void preUpdate(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public String getName(){return name;} public void setName(String name){this.name=name;} public String getTimezone(){return timezone;} public void setTimezone(String timezone){this.timezone=timezone;} public String getLanguage(){return language;} public void setLanguage(String language){this.language=language;} public String getHumanTransferPhone(){return humanTransferPhone;} public void setHumanTransferPhone(String humanTransferPhone){this.humanTransferPhone=humanTransferPhone;} public BusinessStatus getStatus(){return status;} public void setStatus(BusinessStatus status){this.status=status;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
