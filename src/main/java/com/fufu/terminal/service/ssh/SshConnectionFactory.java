package com.fufu.terminal.service.ssh;

import com.fufu.terminal.command.model.SshConnectionConfig;
import com.fufu.terminal.config.properties.ScriptExecutionProperties;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * SSH Connection Factory for Apache Commons Pool2
 * Creates and manages SSH connections with proper lifecycle management
 */
@Slf4j
@RequiredArgsConstructor
public class SshConnectionFactory extends BasePooledObjectFactory<SshConnection> {

    private final SshConnectionConfig config;
    private final ScriptExecutionProperties properties;

    @Override
    public SshConnection create() throws Exception {
        log.debug("Creating new SSH connection to {}@{}:{}",
                config.getUsername(), config.getHost(), config.getPort());

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());

            // Enhanced security configuration
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            session.setConfig("kex", "diffie-hellman-group14-sha256,diffie-hellman-group-exchange-sha256");
            session.setConfig("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr");
            session.setConfig("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr");
            session.setConfig("mac.s2c", "hmac-sha2-256,hmac-sha2-512");
            session.setConfig("mac.c2s", "hmac-sha2-256,hmac-sha2-512");

            // Set timeouts
            session.setTimeout((int) properties.getSsh().getTimeouts().getSessionTimeout().toMillis());
            session.setPassword(config.getPassword());

            // Connect with timeout
            session.connect((int) properties.getSsh().getTimeouts().getConnectionTimeout().toMillis());

            log.debug("SSH session established successfully to {}@{}:{}",
                    config.getUsername(), config.getHost(), config.getPort());

            return new SshConnection(jsch,session, config.getHost(), config.getPort(), config.getUsername());

        } catch (JSchException e) {
            log.error("Failed to create SSH connection to {}@{}:{}: {}",
                    config.getUsername(), config.getHost(), config.getPort(), e.getMessage());
            throw new Exception("Failed to create SSH connection: " + e.getMessage(), e);
        }
    }

    @Override
    public PooledObject<SshConnection> wrap(SshConnection connection) {
        return new DefaultPooledObject<>(connection);
    }

    @Override
    public boolean validateObject(PooledObject<SshConnection> pooledObject) {
        SshConnection connection = pooledObject.getObject();
        try {
            if (connection == null || !connection.isConnected()) {
                log.debug("Connection validation failed: connection is null or not connected");
                return false;
            }

            Session session = connection.getJschSession();
            if (session == null || !session.isConnected()) {
                log.debug("Connection validation failed: session is null or not connected");
                return false;
            }

            // Test with a simple command
            String validationQuery = properties.getSsh().getConnectionPool().getValidationQuery();
            com.fufu.terminal.command.CommandResult result =
                com.fufu.terminal.command.SshCommandUtil.executeCommand(connection, validationQuery);

            boolean isValid = result != null && result.getExitStatus() == 0;
            log.debug("Connection validation result: {}", isValid);
            return isValid;

        } catch (Exception e) {
            log.debug("Connection validation failed with exception", e);
            return false;
        }
    }

    @Override
    public void destroyObject(PooledObject<SshConnection> pooledObject) throws Exception {
        SshConnection connection = pooledObject.getObject();
        if (connection != null) {
            try {
                log.debug("Destroying SSH connection to {}@{}:{}",
                        config.getUsername(), config.getHost(), config.getPort());
                connection.getSession().disconnect();
            } catch (Exception e) {
                log.warn("Error destroying SSH connection", e);
            }
        }
    }

    @Override
    public void activateObject(PooledObject<SshConnection> pooledObject) throws Exception {
        SshConnection connection = pooledObject.getObject();
        log.debug("Activating SSH connection to {}@{}:{}",
                config.getUsername(), config.getHost(), config.getPort());

        // Ensure connection is still valid when activated
        if (!validateObject(pooledObject)) {
            throw new Exception("Connection is not valid when activated");
        }
    }

    @Override
    public void passivateObject(PooledObject<SshConnection> pooledObject) throws Exception {
        SshConnection connection = pooledObject.getObject();
        log.debug("Passivating SSH connection to {}@{}:{}",
                config.getUsername(), config.getHost(), config.getPort());

        // Optionally perform cleanup when returning to pool
        // For SSH connections, we usually keep them alive for reuse
    }
}
