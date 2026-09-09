package cl.helvoca.user;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "role")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private RoleCode code;

    @Column(nullable = false, length = 100)
    private String name;

    public UUID getId() { return id; }
    public RoleCode getCode() { return code; }
    public void setCode(RoleCode code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
