package liquibase.sqlgenerator.core;

import static liquibase.statement.DatabaseFunction.CURRENT_DATE_TIME_PLACE_HOLDER;

import java.sql.Timestamp;

import liquibase.database.Database;
import liquibase.datatype.DataTypeFactory;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.DatabaseFunction;
import liquibase.statement.core.LockDatabaseChangeLogStatement;
import liquibase.statement.core.UpdateStatement;
import liquibase.util.NetUtil;

public class LockDatabaseChangeLogGenerator extends AbstractSqlGenerator<LockDatabaseChangeLogStatement> {

    @Override
    public ValidationErrors validate(LockDatabaseChangeLogStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        return new ValidationErrors();
    }

    protected static final String hostname;
    protected static final String hostaddress;
    protected static final String hostDescription = (System.getProperty("liquibase.hostDescription") == null) ? "" :
        ("#" + System.getProperty("liquibase.hostDescription"));

    static {
        try {
            hostname = NetUtil.getLocalHostName();
            hostaddress = NetUtil.getLocalHostAddress();
        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }
    }

    @Override
    public Sql[] generateSql(LockDatabaseChangeLogStatement statement, Database database,
                             SqlGeneratorChain sqlGeneratorChain) {
        String liquibaseSchema = database.getLiquibaseSchemaName();
        String liquibaseCatalog = database.getLiquibaseCatalogName();

        UpdateStatement updateStatement = new UpdateStatement(liquibaseCatalog, liquibaseSchema,
            database.getDatabaseChangeLogLockTableName());

        updateStatement.addNewColumnValue("LOCKED", true);
        updateStatement.addNewColumnValue("LOCKGRANTED",
            new DatabaseFunction(CURRENT_DATE_TIME_PLACE_HOLDER));

        // If this lock gets actively prolonged, fill this column, otherwise leave NULL
        if (statement.isProlongedLock()) {

            updateStatement.addNewColumnValue("LOCKEXPIRES",
                new Timestamp(statement.getLockExpiresOnServer().getTime()));

        } else {
            // for standard lock service, set to NULL
            updateStatement.addNewColumnValue("LOCKEXPIRES", null);
        }

        // also add ID of this instance (can be NULL)
        updateStatement.addNewColumnValue("LOCKEDBYID",
            statement.getLockedById());

        updateStatement.addNewColumnValue("LOCKEDBY",
            hostname + hostDescription + " (" + hostaddress + ")");

        String idColumn = database.escapeColumnName(liquibaseCatalog, liquibaseSchema,
            database.getDatabaseChangeLogTableName(), "ID");

        String lockedColumn = database.escapeColumnName(liquibaseCatalog, liquibaseSchema, database
            .getDatabaseChangeLogTableName(), "LOCKED");

        String falseConverted = DataTypeFactory
            .getInstance()
            .fromDescription("boolean", database)
            .objectToSql(false, database);

        updateStatement.setWhereClause(idColumn + " = 1 AND " + lockedColumn + " = " + falseConverted);

        return SqlGeneratorFactory.getInstance().generateSql(updateStatement, database);

    }
}