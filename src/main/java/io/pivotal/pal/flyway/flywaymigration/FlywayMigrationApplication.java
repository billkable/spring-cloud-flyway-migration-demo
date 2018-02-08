package io.pivotal.pal.flyway.flywaymigration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@SpringBootApplication
//@EnableTask
// TODO Split Application Classes and add conditional on Task
public class FlywayMigrationApplication {
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("migrate-only")
	private String migrateOnly;

	public static void main(String[] args) {
		SpringApplication.run(FlywayMigrationApplication.class, args);
	}

	@Bean
	public CommandLineRunner runner() {
		return new CommandLineRunner() {

			@Override
			public void run(String... args) throws Exception {
				// TODO Failure not handled, need to explicitly handle JDBC Migration Task here

				if (Boolean.getBoolean(migrateOnly))
					logger.info("Completed flyway migration task");
			}
		};
	}
}
