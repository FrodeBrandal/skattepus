package src.skattepus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SkattepusApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkattepusApplication.class, args);
	}

}
