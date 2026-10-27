package br.com.clicklance;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClickLanceApplication {
    public static void main(String[] args) { SpringApplication.run(ClickLanceApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
