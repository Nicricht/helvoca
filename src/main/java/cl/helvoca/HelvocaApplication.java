package cl.helvoca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class HelvocaApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelvocaApplication.class, args);
    }
}
