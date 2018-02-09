# Flyway Database Migration Lab
## Overview
- Different migration requirements
- Simplest migration may be coupled directly as migration with the application (small apps)
- Migrations may be decoupled from the app release/deployment
      - Prestaging migrations prior to release
      - Migration cannot be done eagerly in deployment, but should be run as on-platform task
      - Migrations may be done manually, running migration tools directly against database
      - Complex migrations may require ETL, Map/Reduce, or Event streaming through data pumps
- We will cover eager migrations, and simple on-platform task migrations with flyway

## Eager Migrations
In simplest example, simply including Flyway dependency in the application's dependencies will
autoconfigure flyway as initial Springboot datasource migration.  The database migration scripts
must also be included in the classpath (resources)

- Add flyway-core dependency to project dependencies

            compile('org.flywaydb:flyway-core:5.0.7')

- Add flyway migration scripts to the resource db/migrations path
- Configure flyway - we will assume baseline on migration (automatically set the baseline to
1st version if no baseline already exists)
- Start the application, verify flyway migration is complete.

That's it!

## Complex Migrations
In large or complex applications, or in applications with large or complex databases, it may
be necessary to stage migrations separately from, or ahead of, application deployments.

In this case we can easily run database migrations as one-off tasks, but on same PaaS platform
where running the applications.

Spring Cloud Tasks are a good candidate for application level tracking of migration tasks for
audit trail and monitoring.


### Running Migrations On Platform
On Cloud Foundry we may use Spring Cloud Tasks in combination with Cloud Foundry Tasks, or, 
on Pivotal Cloud Foundry we may use the PCF Scheduler to manage and track Jobs.

In this lab we will choose to implement Spring Cloud Tasks with PCF Scheduled Jobs.

#### Implement Separate Application for Managing and Monitoring Migrations
- Generate Springboot Application

            @SpringBootApplication
            public class FlywayApplication {
                private Logger logger = LoggerFactory.getLogger(this.getClass());

                public static void main(String[] args) {
                    SpringApplication.run(FlywayApplication.class, args);
                }
            }

- Add following dependencies:
      - Actuator
      - Spring Boot Web Starter
      - Spring Boot JDBC Starter
      - Spring Cloud Task Starter
      - Flyway Core
      - JDBC Driver for MySQL

            dependencies {
                  compile 'org.springframework.cloud:spring-cloud-starter-task:1.2.2.RELEASE'
                  compile('org.springframework.boot:spring-boot-starter-jdbc:1.5.10.RELEASE')
                  compile('org.springframework.boot:spring-boot-starter-web:1.5.10.RELEASE')
                  compile('org.springframework.boot:spring-boot-starter-actuator:1.5.10.RELEASE')
                  compile('mysql:mysql-connector-java:6.0.6')
                  compile('org.flywaydb:flyway-core:5.0.7')
                  testCompile('org.springframework.boot:spring-boot-starter-test')
            }

#### Implement Staging and Monitoring
By default Flyway migration strategy is eager -- it will run the migration during Springboot datasource
init by default.

We do not want to eager migrate, we only want to stage the migration files as part of application classpath,
and monitor through actuator.

To disable migration, we can override the Flyway migration strategy to turn the migrate into a Noop, but
still retain staging, and actuator monitoring, but providing an alternate Spring Bean implementation of `FlywayMigrationStrategy`.  We will use a property `migrate.command` as a condition to override the
FlywayMigrationStrategy:

            @Component
            @ConditionalOnExpression("'${migrate.command}'.equals('stage')")
            public class StageMigrationStrategy implements FlywayMigrationStrategy {
                private Logger logger = LoggerFactory.getLogger(this.getClass());

                @Override
                public void migrate(Flyway flyway) {
                    logger.info("Migration staged");
                }
            }

But we also want to be able to leverage the same application to execute tasks, in separate process, separate container.

We will use Spring Cloud Tasks to do so with Command Line Runner in Spring Application.  Use a boolean Conditional Property
called `migrate.task` to enable or disable task mode:


            @Configuration
            @EnableTask
            @ConditionalOnProperty("migrate.task")
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


