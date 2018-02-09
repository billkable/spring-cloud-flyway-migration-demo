package io.pivotal.pal.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableTask
@ConditionalOnProperty("migrate-task")
public class FlywayMigrationTaskConfig {
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Bean
	public CommandLineRunner runner() {
		return new CommandLineRunner() {

			@Override
			public void run(String... args) throws Exception {
				// TODO Failure not handled, need to explicitly handle JDBC Migration Task here
				logger.info("Completed flyway migration task");
			}
		};
	}
}
