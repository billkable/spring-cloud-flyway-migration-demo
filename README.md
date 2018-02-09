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

#### Implement Staging and Monitoring as LRP
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


#### Implement Migration and Validation as Spring Cloud Task
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

#### Default Properties
We need to set up the application property defaults:

            flyway:
              enabled: true
              validate-on-migrate: true
              baseline-on-migrate: true

            migrate:
              task: false
              command: stage # stage, migrate options

            endpoints:
              actuator:
                enabled: true
              flyway:
                enabled: true

            management:
              security:
                enabled: false

#### Stage and Monitor
- Build and Start the FlywayApplication in staging/monitoring mode:

            ./gradlew clean build
            ./gradlew bootRun

- Post the Actuator flyway endpoint, what do you see?

            https://localhost:8080/flyway

- You should see 3 pending migrations

- Shutdown the FlywayApplication

#### Run a Migration

- Start the FlywayApplication in migrate task mode:

            MIGRATE_TASK=true MIGRATE_COMMAND=migrate ./gradlew bootRun

- What happens?  Once the task is complete, the FlywayApplication terminates.

- Log into database, select from TASKS table.  This is default Spring Cloud Task 
mechanism for migration job audit trail

- Start the FlywayApplication in staging/monitoring mode:

            ./gradlew bootRun

- Post the Actuator flyway endpoint, what do you see?

            https://localhost:8080/flyway

- You should see 3 successful migrations

- Shutdown the FlywayApplication

#### Setting Up Flyway Staging, Monitoring and Migration to Run on PCF

- Set up the manifest file for the FlywayApplication.  Its default environment
will launch in staging and monitoring mode.

            ---
            applications:
            - name: person-migration
              memory: 768M
              instances: 1
              path: ./build/libs/bootified-flyway-migration-0.0.1-SNAPSHOT.jar

            routes:
            - route: person-migration-{your initials}.cfapps.io

            buildpack: java_buildpack

            services:
            - person-db-service
            - job-scheduler

- Create the database service if not already created.  This will be
the database that is bound to flyway config deployed in FlywayApplication
through the Spring Cloud Foundry Connector, no endpoint configuration
is required:

            cf create-service cleardb spark person-db-service

- Create the job-scheduler service:

            cf create-service scheduler-for-pcf standard job-scheduler

- Download and install the cf job scheduler plugins

#### Deploying Flyway App

- Push the FlywayApplication.  This will stage and set up actuator monitor:

            cf push

- Once push is complete, create job for migration.  You will need to copy the
run command from output of cf push, and add environment variables to enable
task mode, and migrate command.  It should look something like this:

            cf create-job person-migration-bkable migrate-person 'MIGRATE_TASK=true MIGRATE_COMMAND=migrate JAVA_OPTS="-agentpath:$PWD/.java-buildpack/open_jdk_jre/bin/jvmkill-1.10.0_RELEASE=printHeapHistogram=1 -Djava.io.tmpdir=$TMPDIR -Djava.ext.dirs=$PWD/.java-buildpack/container_security_provider:$PWD/.java-buildpack/open_jdk_jre/lib/ext -Djava.security.properties=$PWD/.java-buildpack/security_providers/java.security $JAVA_OPTS" && CALCULATED_MEMORY=$($PWD/.java-buildpack/open_jdk_jre/bin/java-buildpack-memory-calculator-3.9.0_RELEASE -totMemory=$MEMORY_LIMIT -stackThreads=300 -loadedClasses=14717 -poolType=metaspace -vmOptions="$JAVA_OPTS") && echo JVM Memory Configuration: $CALCULATED_MEMORY && JAVA_OPTS="$JAVA_OPTS $CALCULATED_MEMORY" && SERVER_PORT=$PORT eval exec $PWD/.java-buildpack/open_jdk_jre/bin/java $JAVA_OPTS -cp $PWD/. org.springframework.boot.loader.JarLauncher'

#### Monitoring PCF Migrations
- Post actuator endpoint for your FlywayApplication:

            https://person-migration-{your initials}.cfapps.io/flyway

- You should see 3 pending (staged) migrations

#### Executing a Migration
- Given we are using the PCF Scheduler, we should schedule the job.  For the lab
we will kick off manually:

            cf run-job migrate-person

- You can view for successful completion through job history:

            cf job-history migrate-person

- Post actuator endpoint for your FlywayApplication:

            https://person-migration-{your initials}.cfapps.io/flyway

- You should see 3 successful migrations

## Wrap Up
- What happens if a migration fails?
      - If it fails mid-flight without DDL transactions, may require rollback 
      of database, or compensating undo.  Flyway Pro provides official Undo
      support; however, the same may be another versioned migration in the
      community addition.
      - If migration fails due to SQL syntax, the migration may be repaired
      through Flyway API, and the migration re-run.
- What if we need to clean a non-prod database?
      - Flyway supports clean option through configuration, or API.  It 
      must be disabled for production
- Can we validate migrations, without running it?
      - Yes, the `validate-on-migrate` option does this, but validate API
      may be invoked following migration

## Extras
- Extend the FlywayApplication with the following:
      - Ability to run a task to repair migration
      - Ability to run a task to clean the database
      - Ability to run a task to validate migrations