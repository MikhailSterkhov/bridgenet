package me.moonways.bridgenet.jdbc.core.wrap;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.moonways.bridgenet.jdbc.core.observer.event.*;
import me.moonways.bridgenet.jdbc.core.util.SqlFunction;
import me.moonways.bridgenet.jdbc.core.util.result.Result;
import me.moonways.bridgenet.jdbc.core.ConnectionID;
import me.moonways.bridgenet.jdbc.core.TransactionIsolation;
import me.moonways.bridgenet.jdbc.core.TransactionState;
import me.moonways.bridgenet.jdbc.core.observer.DatabaseObserver;
import me.moonways.bridgenet.jdbc.core.observer.Observable;
import me.moonways.bridgenet.jdbc.core.observer.event.*;
import me.moonways.bridgenet.jdbc.core.security.Credentials;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Builder
public class JdbcWrapper {

    private static final ExecutorService JDBC_THREADS_POOL = Executors.newCachedThreadPool();

    private static final int TRANSACTION_ISOLATION_LEVEL = Connection.TRANSACTION_SERIALIZABLE;
    private static final int VALID_TIMEOUT = 3500;

    @RequiredArgsConstructor
    private static class PreparedQuerySession {

        private final PreparedStatement statement;
        private final boolean useGeneratedKeys;
    }

    private final ConnectionID connectionID;
    private final Credentials credentials;
    private final Thread.UncaughtExceptionHandler exceptionHandler;

    private Connection jdbc;

    private List<DatabaseObserver> observers;
    private boolean currentlyWorker;

    @SneakyThrows
    public boolean isConnected() {
        return jdbc != null && !jdbc.isClosed() && jdbc.isValid(VALID_TIMEOUT);
    }

    public synchronized void addObserver(@NotNull DatabaseObserver observer) {
        if (observers == null) {
            observers = new CopyOnWriteArrayList<>();
        }
        observers.add(observer);
    }

    private void observe(@NotNull Observable event) {
        if (observers != null) {
            observers.forEach(observer -> observer.observe(event));
        }
    }

    public synchronized void connect() {
        try {
            if (jdbc == null || !jdbc.isValid(VALID_TIMEOUT)) {
                observe(new DbConnectEvent(System.currentTimeMillis(), connectionID));
                initConnection();
            }
        } catch (SQLException exception) {
            exceptionHandler.uncaughtException(Thread.currentThread(), exception);
        }
    }

    private void initConnection() throws SQLException {
        String passwordString = new String(credentials.getPassword());
        jdbc = DriverManager.getConnection(credentials.getUri(), credentials.getUsername(), passwordString);
    }

    public void reconnect() {
        try {
            if (jdbc == null || jdbc.isClosed() || !jdbc.isValid(VALID_TIMEOUT)) {
                observe(new DbReconnectPreprocessEvent(System.currentTimeMillis(), connectionID));
                connect();
            }
        } catch (SQLException exception) {
            exceptionHandler.uncaughtException(Thread.currentThread(), exception);
        }
    }

    public synchronized void close() {
        try {
            if (jdbc != null && (jdbc.isClosed() || !jdbc.isValid(VALID_TIMEOUT))) {
                observe(new DbClosedEvent(System.currentTimeMillis(), connectionID));
                jdbc.close();
            }
        } catch (SQLException exception) {
            exceptionHandler.uncaughtException(Thread.currentThread(), exception);
        }
    }

    @SneakyThrows
    private Result<ResultWrapper> executeOrdered(String sql,
                                                 SqlFunction<PreparedQuerySession, ResultWrapper> resultLookup) {
        final Thread thread = Thread.currentThread();
        final Result<ResultWrapper> result = Result.ofEmpty();
        try {
            final int autoGeneratedKeys = isGeneratedKeysSupported(sql) ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
            final PreparedStatement statement = jdbc.prepareStatement(sql, autoGeneratedKeys);

            observe(new DbRequestPreprocessEvent(System.currentTimeMillis(), connectionID, sql));

            result.beginIntent()
                    .whenCompleted(__ -> observe(
                            new DbRequestCompletedEvent(System.currentTimeMillis(), connectionID, sql, result)));

            CompletableFuture.runAsync(() ->
                    result.completeIntent(() -> {
                        try {
                            return resultLookup.get(new PreparedQuerySession(statement,
                                    autoGeneratedKeys == Statement.RETURN_GENERATED_KEYS));

                        } catch (SQLException exception) {
                            exceptionHandler.uncaughtException(thread, exception);
                            return null;
                        }
                    }), JDBC_THREADS_POOL).join();

        } catch (SQLException exception) {
            exceptionHandler.uncaughtException(thread, exception);
        }
        return result;
    }

    public Result<ResultWrapper> executeUpdate(String sql) {
        return executeOrdered(sql, (session) -> {
            long affectedRows = isLargeUpdate(sql)
                    ? session.statement.executeLargeUpdate()
                    : session.statement.executeUpdate();

            ResultSet generatedKeys = null;

            if (session.useGeneratedKeys) {
                generatedKeys = session.statement.getGeneratedKeys();
            }

            return ResultWrapper.builder()
                    .affectedRows(affectedRows)
                    .statement(session.statement)
                    .result(generatedKeys)
                    .build();
        });
    }

    public Result<ResultWrapper> executeFetch(String sql) {
        return executeOrdered(sql, (session) -> {
            final ResultSet resultSet = session.statement.executeQuery();

            return ResultWrapper.builder()
                    .affectedRows(resultSet.getFetchSize())
                    .statement(session.statement)
                    .result(resultSet)
                    .build();
        });
    }

    @SuppressWarnings("MagicConstant")
    public synchronized void setTransactionIsolation(TransactionIsolation isolation) {
        try {
            if (jdbc.getAutoCommit()) {
                jdbc.setTransactionIsolation(isolation.getLevel());
            }
        } catch (SQLException exception) {
            exceptionHandler.uncaughtException(Thread.currentThread(), exception);
        }
    }

    public synchronized void setTransactionState(TransactionState state) {
        Thread thread = Thread.currentThread();
        switch (state) {
            case ACTIVE: {
                try {
                    jdbc.setAutoCommit(false);
                } catch (SQLException exception) {
                    exceptionHandler.uncaughtException(thread, exception);
                }
                break;
            }
            case INACTIVE: {
                try {
                    jdbc.commit();
                    //jdbc.setTransactionIsolation(Connection.TRANSACTION_NONE); todo
                    jdbc.setAutoCommit(true);

                } catch (SQLException exception) {
                    try {
                        jdbc.rollback();
                    } catch (SQLException e) {
                        exceptionHandler.uncaughtException(thread, exception);
                    }
                    exceptionHandler.uncaughtException(thread, exception);
                }
                break;
            }
        }
    }

    private boolean isGeneratedKeysSupported(String sql) {
        try {
            Statement statement = jdbc.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.close();

            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLargeUpdate(String sql) {
        return sql != null && (sql.trim().toUpperCase().startsWith("UPDATE")
                || sql.trim().toUpperCase().startsWith("DELETE"));
    }
}