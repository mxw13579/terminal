# SSH Terminal System - Comprehensive Test Suite Configuration

## Test Dependencies Required

Add these dependencies to your `pom.xml` for complete test coverage:

```xml
<!-- Testing Dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- AssertJ for fluent assertions -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>

<!-- Awaitility for async testing -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>

<!-- TestContainers for integration testing -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>

<!-- WebSocket testing -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-websocket</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-messaging</artifactId>
    <scope>test</scope>
</dependency>

<!-- H2 for in-memory testing -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

## Maven Plugin Configuration

Add these plugins to your `pom.xml` for enhanced testing capabilities:

```xml
<build>
    <plugins>
        <!-- Surefire Plugin for Unit Tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.0.0-M9</version>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                </includes>
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                    <exclude>**/*PerformanceTest.java</exclude>
                </excludes>
                <argLine>-Xmx1024m</argLine>
            </configuration>
        </plugin>

        <!-- Failsafe Plugin for Integration Tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.0.0-M9</version>
            <configuration>
                <includes>
                    <include>**/*IntegrationTest.java</include>
                </includes>
                <argLine>-Xmx2048m</argLine>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <!-- JaCoCo Plugin for Code Coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.8</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>INSTRUCTION</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.85</minimum>
                                    </limit>
                                    <limit>
                                        <counter>BRANCH</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.80</minimum>
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Test Profiles

Add these profiles to your `pom.xml` for different test scenarios:

```xml
<profiles>
    <!-- Unit Tests Only -->
    <profile>
        <id>unit-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*Test.java</include>
                        </includes>
                        <excludes>
                            <exclude>**/*IntegrationTest.java</exclude>
                            <exclude>**/*PerformanceTest.java</exclude>
                        </excludes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Integration Tests Only -->
    <profile>
        <id>integration-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*IntegrationTest.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Performance Tests Only -->
    <profile>
        <id>performance-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*PerformanceTest.java</include>
                        </includes>
                        <argLine>-Xmx4096m -XX:+UseG1GC</argLine>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- All Tests -->
    <profile>
        <id>all-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*Test.java</include>
                        </includes>
                        <argLine>-Xmx4096m</argLine>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

## Test Execution Commands

### Individual Test Categories

```bash
# Unit Tests Only
mvn test -Punit-tests

# Integration Tests Only  
mvn test -Pintegration-tests

# Performance Tests Only
mvn test -Pperformance-tests

# All Tests
mvn test -Pall-tests
```

### Specific Test Classes

```bash
# Run specific test class
mvn -Dtest=ScriptTypeTest test

# Run specific test method
mvn -Dtest=ScriptTypeTest#shouldHaveExactlyFourScriptTypes test

# Run multiple test classes
mvn -Dtest=ScriptTypeTest,BuiltinScriptRegistryTest test
```

### Coverage Reports

```bash
# Generate coverage report
mvn jacoco:prepare-agent test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## Test Configuration Properties

Create `src/test/resources/application-test.yml`:

```yaml
# Test-specific configuration
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  h2:
    console:
      enabled: true

# WebSocket test configuration
app:
  websocket:
    allowed-origins: "*"
    heartbeat:
      client: 5000
      server: 5000
    message:
      max-size: 64000
    timeout:
      send: 10000

# Logging configuration for tests
logging:
  level:
    com.fufu.terminal: DEBUG
    org.springframework.test: INFO
    org.testcontainers: INFO
    org.springframework.web.socket: DEBUG

# Test-specific SSH configuration
ssh:
  test:
    host: localhost
    port: 22
    username: testuser
    password: testpass
    timeout: 30000
```

## Docker Compose for Test Environment

Create `docker-compose.test.yml`:

```yaml
version: '3.8'
services:
  mysql-test:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: testpass
      MYSQL_DATABASE: ssh_terminal_test
      MYSQL_USER: testuser
      MYSQL_PASSWORD: testpass
    ports:
      - "3307:3306"
    tmpfs:
      - /var/lib/mysql

  ssh-server-test:
    image: panubo/sshd:latest
    environment:
      SSH_USERS: "testuser:1000:1000"
      SSH_ENABLE_PASSWORD_AUTH: "true"
    ports:
      - "2222:22"
```

Start test environment:
```bash
docker-compose -f docker-compose.test.yml up -d
```