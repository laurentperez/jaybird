/*
 * $Id$
 *
 * Firebird Open Source JavaEE Connector - JDBC Driver
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a source control history command.
 *
 * All rights reserved.
 */
package org.firebirdsql.jdbc;

import org.firebirdsql.common.FBJUnit4TestBase;
import org.firebirdsql.gds.ISCConstants;
import org.firebirdsql.util.FirebirdSupportInfo;
import org.hamcrest.number.OrderingComparison;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.firebirdsql.common.DdlHelper.executeCreateTable;
import static org.firebirdsql.common.DdlHelper.executeDDL;
import static org.firebirdsql.common.FBTestProperties.*;
import static org.firebirdsql.common.JdbcResourceHelper.closeQuietly;
import static org.firebirdsql.common.matchers.SQLExceptionMatchers.*;
import static org.firebirdsql.util.FirebirdSupportInfo.supportInfoFor;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * @author <a href="mailto:rrokytskyy@users.sourceforge.net">Roman Rokytskyy</a>
 * @author <a href="mailto:mrotteveel@users.sourceforge.net">Mark Rotteveel</a>
 */
public class TestFBPreparedStatement extends FBJUnit4TestBase {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private static final Random rnd = new Random();

    public static final String CREATE_GENERATOR = "CREATE GENERATOR test_generator";

    public static final String CREATE_TEST_BLOB_TABLE =
              "CREATE TABLE test_blob ("
            + "  ID INTEGER, "
            + "  OBJ_DATA BLOB, "
            + "  TS_FIELD TIMESTAMP, "
            + "  T_FIELD TIME "
            + ")";

    public static final String CREATE_TEST_CHARS_TABLE =
              "CREATE TABLE TESTTAB ("
            + "ID INTEGER, "
            + "FIELD1 VARCHAR(10) NOT NULL PRIMARY KEY,"
            + "FIELD2 VARCHAR(30),"
            + "FIELD3 VARCHAR(20),"
            + "FIELD4 FLOAT,"
            + "FIELD5 CHAR,"
            + "FIELD6 VARCHAR(5),"
            + "FIELD7 CHAR(1),"
            + "num_field numeric(9,2),"
            + "UTFFIELD CHAR(1) CHARACTER SET UTF8"
            + ")";

    public static final String CREATE_UNRECOGNIZED_TR_TABLE =
              "CREATE TABLE t1("
            + "  c1 CHAR(2) CHARACTER SET ASCII NOT NULL, "
            + "  c2 BLOB SUB_TYPE TEXT CHARACTER SET ASCII NOT NULL "
            + ")";

    public static final String ADD_CONSTRAINT_T1_C1 = "ALTER TABLE t1 ADD CONSTRAINT t1_c1 PRIMARY KEY (c1)";

    public static final String INIT_T1 = "INSERT INTO t1 VALUES ('XX', 'no more bugs')";

    public static final String TEST_STRING = "This is simple test string.";
    public static final String ANOTHER_TEST_STRING = "Another test string.";

    private static final int DATA_ITEMS = 5;
    private static final String CREATE_TABLE = "CREATE TABLE test ( col1 INTEGER )";
    private static final String INSERT_DATA = "INSERT INTO test(col1) VALUES(?)";
    private static final String SELECT_DATA = "SELECT col1 FROM test ORDER BY col1";

    private Connection con;

    @Before
    public void setUp() throws Exception {
        con = getConnectionViaDriverManager();
        Statement stmt = con.createStatement();
        try {
            executeCreateTable(con, CREATE_TEST_BLOB_TABLE);
            executeCreateTable(con, CREATE_UNRECOGNIZED_TR_TABLE);
            executeDDL(con, ADD_CONSTRAINT_T1_C1);
            stmt.executeUpdate(INIT_T1);
            executeCreateTable(con, CREATE_TEST_CHARS_TABLE);
            executeDDL(con, CREATE_GENERATOR);
            executeCreateTable(con, CREATE_TABLE);
            prepareTestData();
        } finally {
            stmt.close();
        }
    }

    @After
    public void tearDown() throws Exception {
        closeQuietly(con);
    }

    @Test
    public void testModifyBlob() throws Exception {
        final int id = 1;

        PreparedStatement insertPs = con.prepareStatement("INSERT INTO test_blob (id, obj_data) VALUES (?,?)");
        try {
            insertPs.setInt(1, id);
            insertPs.setBytes(2, TEST_STRING.getBytes());

            int inserted = insertPs.executeUpdate();

            assertEquals("Row should be inserted.", 1, inserted);
        } finally {
            closeQuietly(insertPs);
        }

        checkSelectString(TEST_STRING, id);

        // Update item
        PreparedStatement updatePs = con.prepareStatement("UPDATE test_blob SET obj_data=? WHERE id=?");
        try {
            updatePs.setBytes(1, ANOTHER_TEST_STRING.getBytes());
            updatePs.setInt(2, id);
            updatePs.executeUpdate();

            updatePs.clearParameters();

            checkSelectString(ANOTHER_TEST_STRING, id);

            updatePs.setBytes(1, TEST_STRING.getBytes());
            updatePs.setInt(2, id + 1);
            int updated = updatePs.executeUpdate();

            assertEquals("No rows should be updated.", 0, updated);

            checkSelectString(ANOTHER_TEST_STRING, id);
        } finally {
            closeQuietly(updatePs);
        }
    }

    /**
     * The method {@link java.sql.Statement#executeQuery(String)} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecuteQuery_String() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.executeQuery("SELECT * FROM test_blob");
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#executeUpdate(String)} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecuteUpdate_String() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.executeUpdate("SELECT * FROM test_blob");
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#execute(String)} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecute_String() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.execute("SELECT * FROM test_blob");
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#addBatch(String)} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedAddBatch_String() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.addBatch("SELECT * FROM test_blob");
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#executeUpdate(String, int)} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecuteUpdate_String_int() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.executeUpdate("SELECT * FROM test_blob", Statement.NO_GENERATED_KEYS);
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#execute(String, int[])} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecuteUpdate_String_intArr() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.executeUpdate("SELECT * FROM test_blob", new int[]{ 1 });
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#executeUpdate(String, String[])} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecuteUpdate_String_StringArr() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.executeUpdate("SELECT * FROM test_blob", new String[]{ "col" });
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#execute(String, int)} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecute_String_int() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.execute("SELECT * FROM test_blob", Statement.NO_GENERATED_KEYS);
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#execute(String, int[])} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecute_String_intArr() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.execute("SELECT * FROM test_blob", new int[]{ 1 });
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * The method {@link java.sql.Statement#execute(String, String[])} should not work on PreparedStatement.
     */
    @Test
    public void testUnsupportedExecute_String_StringArr() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RDB$DATABASE");
        try {
            expectStatementOnlyException();

            ps.execute("SELECT * FROM test_blob", new String[]{ "col" });
        } finally {
            closeQuietly(ps);
        }
    }

    private void expectStatementOnlyException() {
        expectedException.expect(allOf(
                isA(SQLException.class),
                sqlState(equalTo(FBSQLException.SQL_STATE_GENERAL_ERROR)),
                message(equalTo(FBPreparedStatement.METHOD_NOT_SUPPORTED))
        ));
    }

    void checkSelectString(String stringToTest, int id) throws Exception {
        PreparedStatement selectPs = con.prepareStatement("SELECT obj_data FROM test_blob WHERE id = ?");
        try {
            selectPs.setInt(1, id);
            ResultSet rs = selectPs.executeQuery();

            assertTrue("There must be at least one row available.", rs.next());
            assertEquals("Selected string must be equal to inserted one.", stringToTest, rs.getString(1));
            assertFalse("There must be exactly one row.", rs.next());

            rs.close();
        } finally {
            closeQuietly(selectPs);
        }
    }

    @Test
    public void testGenerator() throws Exception {
        PreparedStatement ps = con.prepareStatement("SELECT gen_id(test_generator, 1) as new_value FROM rdb$database");
        try {
            ResultSet rs = ps.executeQuery();

            assertTrue("Should get at least one row", rs.next());

            rs.getLong("new_value");

            assertFalse("should have only one row", rs.next());

            rs.close();
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * Test case to reproduce problem with the connection when "operation was
     * cancelled" happens. Bug is fixed, however due to workaround for this
     * problem (@see org.firebirdsql.jdbc.field.FBWorkaroundStringField) this
     * test case is no longer relevant. In order to make it execute correctly
     * one has to remove this workaround.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    @Ignore(value="Broken due to FBWorkaroundStringField")
    public void testOpCancelled() throws Exception {
        PreparedStatement prep = con
                .prepareStatement("INSERT INTO TESTTAB (FIELD1, FIELD3, FIELD4, FIELD5 ) VALUES ( ?, ?, ?, ? )");
        try {
            for (int i = 0; i < 5; i++) {
                if (i == 0) {
                    prep.setObject(1, "0123456789");
                    prep.setObject(2, "01234567890123456789");
                    prep.setObject(3, "1259.9");
                    prep.setObject(4, "A");
                }
                if (i == 1) {
                    prep.setObject(1, "0123456787");
                    prep.setObject(2, "012345678901234567890");
                    prep.setObject(3, "0.9");
                    prep.setObject(4, "B");
                }
                if (i == 2) {
                    prep.setObject(1, "0123456788");
                    prep.setObject(2, "Fld3-Rec3");
                    prep.setObject(3, "0.9");
                    prep.setObject(4, "B");
                }
                if (i == 3) {
                    prep.setObject(1, "0123456780");
                    prep.setObject(2, "Fld3-Rec4");
                    prep.setObject(3, "1299.5");
                    prep.setObject(4, "Q");
                }
                if (i == 4) {
                    prep.setObject(1, "0123456779");
                    prep.setObject(2, "Fld3-Rec5");
                    prep.setObject(3, "1844");
                    prep.setObject(4, "Z");
                }
                prep.execute();
            }
        } finally {
            closeQuietly(prep);
        }
    }

    /**
     * Test if parameters are correctly checked for their length.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testLongParameter() throws Exception {
        Statement stmt = con.createStatement();
        try {
            stmt.execute("INSERT INTO testtab(id, field1, field6) VALUES(1, '', 'a')");
        } finally {
            closeQuietly(stmt);
        }

        con.setAutoCommit(false);

        PreparedStatement ps = con.prepareStatement("UPDATE testtab SET field6=? WHERE id = 1");
        try {
            try {
                ps.setString(1, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
                ps.execute();
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
            }
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * Test if batch execution works correctly.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testBatch() throws Exception {
        Statement s = con.createStatement();
        try {
            s.executeUpdate("CREATE TABLE foo ("
                    + "bar varchar(64) NOT NULL, "
                    + "baz varchar(8) NOT NULL, "
                    + "CONSTRAINT pk_foo PRIMARY KEY (bar, baz))");
        } finally {
            closeQuietly(s);
        }

        PreparedStatement ps = con
                .prepareStatement("Insert into foo values (?, ?)");
        try {
            ps.setString(1, "one");
            ps.setString(2, "two");
            ps.addBatch();
            ps.executeBatch();
            ps.clearBatch();
            ps.setString(1, "one");
            ps.setString(2, "three");
            ps.addBatch();
            ps.executeBatch();
            ps.clearBatch();
        } finally {
            closeQuietly(ps);
        }
    }

    @Test
    public void testTimestampWithCalendar() throws Exception {
        Properties props = new Properties();
        props.putAll(getDefaultPropertiesForConnection());
        props.setProperty("timestamp_uses_local_timezone", "");
        Connection connection = DriverManager.getConnection(getUrl(), props);

        try {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO test_blob(id, ts_field) VALUES (?, ?)");

            try {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+01"));
                Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

                Timestamp ts = new Timestamp(calendar.getTime().getTime());

                stmt.setInt(1, 2);
                stmt.setTimestamp(2, ts, calendar);
                stmt.execute();

                stmt.setInt(1, 3);
                stmt.setTimestamp(2, ts, utcCalendar);
                stmt.execute();
            } finally {
                closeQuietly(stmt);
            }

            Statement selectStmt = connection.createStatement();
            try {
                ResultSet rs = selectStmt
                        .executeQuery("SELECT id, CAST(ts_field AS VARCHAR(30)), ts_field FROM test_blob");

                Timestamp ts2 = null;
                Timestamp ts3 = null;

                String ts2AsStr = null;
                String ts3AsStr = null;

                final int maxLength = 22;

                while (rs.next()) {
                    switch (rs.getInt(1)) {
                    case 2:
                        ts2 = rs.getTimestamp(3);
                        ts2AsStr = rs.getString(2);
                        ts2AsStr = ts2AsStr.substring(0, Math.min(ts2AsStr.length(), maxLength));
                        break;

                    case 3:
                        ts3 = rs.getTimestamp(3);
                        ts3AsStr = rs.getString(2);
                        ts3AsStr = ts3AsStr.substring(0, Math.min(ts3AsStr.length(), maxLength));
                        break;
                    }
                }

                assertNotNull(ts2);
                assertNotNull(ts3);

                assertEquals("Timestamps 2 and 3 should differ for 3600 seconds.", 3600 * 1000,
                        Math.abs(ts2.getTime() - ts3.getTime()));
                String ts2ToStr = ts2.toString();
                ts2ToStr = ts2ToStr.substring(0, Math.min(ts2ToStr.length(), maxLength));
                assertEquals("Server should see the same timestamp", ts2AsStr, ts2ToStr);
                String ts3ToStr = ts3.toString();
                ts3ToStr = ts3ToStr.substring(0, Math.min(ts3ToStr.length(), maxLength));
                assertEquals("Server should see the same timestamp", ts3AsStr, ts3ToStr);
            } finally {
                closeQuietly(selectStmt);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    @Test
    public void testTimeWithCalendar() throws Exception {
        Properties props = new Properties();
        props.putAll(getDefaultPropertiesForConnection());
        props.setProperty("timestamp_uses_local_timezone", "");
        Connection connection = DriverManager.getConnection(getUrl(), props);

        try {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO test_blob(id, t_field) VALUES (?, ?)");

            try {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+01"));
                Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

                Time t = new Time(calendar.getTime().getTime());

                stmt.setInt(1, 2);
                stmt.setTime(2, t, calendar);
                stmt.execute();

                stmt.setInt(1, 3);
                stmt.setTime(2, t, utcCalendar);
                stmt.execute();
            } finally {
                closeQuietly(stmt);
            }

            Statement selectStmt = connection.createStatement();
            try {
                ResultSet rs = selectStmt
                        .executeQuery("SELECT id, CAST(t_field AS VARCHAR(30)), t_field FROM test_blob");

                Time t2 = null;
                Time t3 = null;

                String t2Str = null;
                String t3Str = null;

                while (rs.next()) {
                    switch (rs.getInt(1)) {
                        case 2:
                            t2 = rs.getTime(3);
                            t2Str = rs.getString(2);
                            break;

                        case 3:
                            t3 = rs.getTime(3);
                            t3Str = rs.getString(2);
                            break;
                    }
                }

                assertNotNull(t2);
                assertNotNull(t3);

                assertEquals("Timestamps 2 and 3 should differ for 3600 seconds.", 3600 * 1000,
                        Math.abs(t2.getTime() - t3.getTime()));
                assertEquals("Server should see the same timestamp", t2Str.substring(0, 8), t2.toString());
                assertEquals("Server should see the same timestamp", t3Str.substring(0, 8), t3.toString());

            } finally {
                closeQuietly(selectStmt);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Test if failure in setting the parameter leaves the driver in correct
     * state (i.e. "not all params were set").
     *
     * @throws Exception
     */
    @Test
    public void testBindParameter() throws Exception {
        con.setAutoCommit(false);

        PreparedStatement ps = con.prepareStatement("UPDATE testtab SET field1 = ? WHERE id = ?");
        try {
            try {
                ps.setString(1, "veeeeeeeeeeeeeeeeeeeeery looooooooooooooooooooooong striiiiiiiiiiiiiiiiiiing");
            } catch (DataTruncation ex) {
                // ignore
            }

            ps.setInt(2, 1);

            try {
                ps.execute();
            } catch (FBMissingParameterException ex) {
                // correct (not using expected exception here due to following statements!)
            }
        } finally {
            closeQuietly(ps);
        }

        Statement stmt = con.createStatement();
        try {
            stmt.execute("SELECT 1 FROM RDB$DATABASE");
        } finally {
            closeQuietly(stmt);
        }
    }

    /**
     * Test if failure in setting the parameter leaves the driver in correct
     * state (i.e. "not all params were set").
     *
     * @throws Exception
     */
    @Test
    @Ignore("Currently not working as expected due to implementation change (or bug in original implementation/expectation)")
    public void testLikeParameter() throws Exception {
        con.setAutoCommit(false);

        PreparedStatement ps = con.prepareStatement("SELECT * FROM testtab WHERE field7 LIKE ?");
        try {
            try {
                ps.setString(1, "%a%");
                ps.executeQuery();
                // fail("should throw data truncation");
            } catch (DataTruncation ex) {
                // ignore
            }
        } finally {
            ps.close();
        }

        Statement stmt = con.createStatement();
        try {
            stmt.execute("SELECT 1 FROM RDB$DATABASE");
        } finally {
            closeQuietly(stmt);
        }
    }

    @Test
    public void testGetExecutionPlan() throws SQLException {
        FBPreparedStatement stmt = (FBPreparedStatement) con.prepareStatement("SELECT * FROM TESTTAB WHERE ID = 2");
        try {
            String executionPlan = stmt.getExecutionPlan();
            assertThat("Ensure that a valid execution plan is retrieved", executionPlan, containsString("TESTTAB"));
        } finally {
            closeQuietly(stmt);
        }
    }

    protected void checkStatementType(String query, int expectedStatementType, String assertionMessage) throws SQLException {
        FBPreparedStatement stmt = (FBPreparedStatement) con.prepareStatement(query);
        try {
            assertEquals(assertionMessage, expectedStatementType, stmt.getStatementType());
        } finally {
            closeQuietly(stmt);
        }
    }

    @Test
    public void testGetStatementType_Select() throws SQLException {
        checkStatementType("SELECT * FROM TESTTAB", FirebirdPreparedStatement.TYPE_SELECT,
                "TYPE_SELECT should be returned for a SELECT statement");
    }

    @Test
    public void testGetStatementType_Insert() throws SQLException {
        checkStatementType("INSERT INTO testtab(id, field1, field6) VALUES(?, ?, ?)",
                FirebirdPreparedStatement.TYPE_INSERT, "TYPE_INSERT should be returned for an INSERT statement");
    }

    @Test
    public void testGetStatementType_Delete() throws SQLException {
        checkStatementType("DELETE FROM TESTTAB WHERE ID = ?", FirebirdPreparedStatement.TYPE_DELETE,
                "TYPE_DELETE should be returned for a DELETE statement");
    }

    @Test
    public void testGetStatementType_Update() throws SQLException {
        checkStatementType("UPDATE TESTTAB SET FIELD1 = ? WHERE ID = ?", FirebirdPreparedStatement.TYPE_UPDATE,
                "TYPE_UPDATE should be returned for an UPDATE statement");
    }

    @Test
    public void testGetStatementType_InsertReturning() throws SQLException {
        checkStatementType("INSERT INTO testtab(field1) VALUES(?) RETURNING id",
                FirebirdPreparedStatement.TYPE_EXEC_PROCEDURE,
                "TYPE_EXEC_PROCEDURE should be returned for an INSERT ... RETURNING statement");
    }

    @Test
    @Ignore
    public void testLikeFullLength() throws Exception {
        Statement stmt = con.createStatement();
        try {
            stmt.execute("INSERT INTO testtab(field1) VALUES('abcdefghij')");
        } finally {
            closeQuietly(stmt);
        }

        PreparedStatement ps = con.prepareStatement("SELECT field1 FROM testtab WHERE field1 LIKE ?");
        try {
            ps.setString(1, "%abcdefghi%");

            ResultSet rs = ps.executeQuery();
            assertTrue("Should find a record.", rs.next());
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * Test if parameters are correctly checked for their length.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testNumeric15_2() throws Exception {
        Properties props = getDefaultPropertiesForConnection();
        props.setProperty("sqlDialect", "1");

        Connection connection = DriverManager.getConnection(getUrl(), props);
        try {
            Statement stmt = connection.createStatement();
            try {
                stmt.execute("INSERT INTO testtab(id, field1, num_field) VALUES(1, '', 10.02)");
            } finally {
                closeQuietly(stmt);
            }

            PreparedStatement ps = connection
                    .prepareStatement("SELECT num_field FROM testtab WHERE id = 1");
            try {
                ResultSet rs = ps.executeQuery();

                assertTrue(rs.next());

                float floatValue = rs.getFloat(1);
                double doubleValue = rs.getDouble(1);
                BigDecimal bigDecimalValue = rs.getBigDecimal(1);

                assertEquals(10.02f, floatValue, 0.001f);
                assertEquals(10.02, doubleValue, 0.001);
                assertEquals(new BigDecimal("10.02"), bigDecimalValue);
            } finally {
                closeQuietly(ps);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    @Test
    public void testInsertReturning() throws Exception {
        FirebirdPreparedStatement stmt = (FirebirdPreparedStatement) con
                .prepareStatement("INSERT INTO testtab(id, field1) VALUES(gen_id(test_generator, 1), 'a') RETURNING id");
        try {
            assertEquals(
                    "TYPE_EXEC_PROCEDURE should be returned for an INSERT...RETURNING statement",
                    FirebirdPreparedStatement.TYPE_EXEC_PROCEDURE,
                    stmt.getStatementType());
            ResultSet rs = stmt.executeQuery();

            assertTrue("Should return at least 1 row", rs.next());
            assertThat("Generator value should be > 0", rs.getInt(1), OrderingComparison.greaterThan(0));
            assertFalse("Should return exactly one row", rs.next());
        } finally {
            closeQuietly(stmt);
        }
    }

    //@formatter:off
    private static final String LONG_RUNNING_STATEMENT =
            "execute block " +
            " as" +
            "     declare variable i integer;" +
            "     declare variable a varchar(100);" +
            " begin" +
            "    i = 1;" +
            "    while(i < 1000000) do begin" +
            "      EXECUTE STATEMENT 'SELECT ' || :i || ' FROM rdb$database' INTO :a;" +
            "      i = i + 1;" +
            "    end" +
            " end";
    //@formatter:on

    @Test
    public void testCancelStatement() throws Exception {
        final FirebirdSupportInfo supportInfo = supportInfoFor(con);
        assumeTrue("Test requires fb_cancel_operations support", supportInfo.supportsCancelOperation());
        assumeTrue("Test requires EXECUTE BLOCK support", supportInfo.supportsExecuteBlock());
        final Statement stmt = con.createStatement();
        final AtomicBoolean cancelFailed = new AtomicBoolean(false);
        try {
            Thread cancelThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(5);
                        stmt.cancel();
                    } catch (SQLException ex) {
                        cancelFailed.set(true);
                    } catch (InterruptedException ex) {
                        // empty
                    }
                }
            }, "cancel-thread");

            cancelThread.start();

            long start = 0;
            try {
                start = System.currentTimeMillis();
                stmt.execute(LONG_RUNNING_STATEMENT);
                fail("Statement should raise a cancel exception");
            } catch (SQLException ex) {
                long end = System.currentTimeMillis();
                System.out.println("testCancelStatement: statement cancelled after " + (end - start) + " milliseconds");
                assertThat("Unexpected exception for cancellation", ex, allOf(
                        message(startsWith(getFbMessage(ISCConstants.isc_cancelled))),
                        errorCode(equalTo(ISCConstants.isc_cancelled)),
                        sqlState(equalTo("HY008"))
                ));
                // everything is fine
            } finally {
                cancelThread.join();
            }
            assertFalse("Issuing statement cancel failed", cancelFailed.get());
        } finally {
            closeQuietly(stmt);
        }
    }

    /**
     * Tests NULL parameter when using {@link PreparedStatement#setNull(int, int)}
     */
    @Test
    public void testParameterIsNullQuerySetNull() throws Throwable {
        createIsNullTestData();

        PreparedStatement ps = con.prepareStatement("SELECT id FROM testtab WHERE field2 = ? OR ? IS NULL ORDER BY 1");
        ResultSet rs;
        try {
            ps.setNull(1, Types.VARCHAR);
            ps.setNull(2, Types.VARCHAR);

            rs = ps.executeQuery();

            assertTrue("Step 1.1 - should get a record.", rs.next());
            assertEquals("Step 1.1 - ID should be equal 1", 1, rs.getInt(1));
            assertTrue("Step 1.2 - should get a record.", rs.next());
            assertEquals("Step 1.2 - ID should be equal 2", 2, rs.getInt(1));
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * Tests NULL parameter when using actual (non-null) value in {@link PreparedStatement#setString(int, String)}
     */
    @Test
    public void testParameterIsNullQueryWithValues() throws Throwable {
        createIsNullTestData();

        PreparedStatement ps = con.prepareStatement("SELECT id FROM testtab WHERE field2 = ? OR ? IS NULL ORDER BY 1");
        ResultSet rs;
        try {
            ps.setString(1, "a");
            ps.setString(2, "a");

            rs = ps.executeQuery();

            assertTrue("Step 2.1 - should get a record.", rs.next());
            assertEquals("Step 2.1 - ID should be equal 1", 1, rs.getInt(1));
            assertFalse("Step 2 - should get only one record.", rs.next());
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * Tests NULL parameter when using null value in {@link PreparedStatement#setString(int, String)}
     */
    @Test
    public void testParameterIsNullQueryWithNull() throws Throwable {
        createIsNullTestData();

        PreparedStatement ps = con.prepareStatement("SELECT id FROM testtab WHERE field2 = ? OR ? IS NULL ORDER BY 1");
        ResultSet rs;
        try {
            ps.setString(1, null);
            ps.setString(2, null);

            rs = ps.executeQuery();

            assertTrue("Step 1.1 - should get a record.", rs.next());
            assertEquals("Step 1.1 - ID should be equal 1", 1, rs.getInt(1));
            assertTrue("Step 1.2 - should get a record.", rs.next());
            assertEquals("Step 1.2 - ID should be equal 2", 2, rs.getInt(1));
        } finally {
            closeQuietly(ps);
        }
    }

    private void createIsNullTestData() throws SQLException {
        Statement stmt = con.createStatement();
        try {
            stmt.executeUpdate("INSERT INTO testtab(id, field1, field2) VALUES (1, '1', 'a')");
            stmt.executeUpdate("INSERT INTO testtab(id, field1, field2) VALUES (2, '2', NULL)");
        } finally {
            stmt.close();
        }
    }

    /**
     * Closing a statement twice should not result in an Exception.
     *
     * @throws SQLException
     */
    @Test
    public void testDoubleClose() throws SQLException {
        PreparedStatement stmt = con.prepareStatement("SELECT 1, 2 FROM RDB$DATABASE");
        stmt.close();
        stmt.close();
    }

    /**
     * Test if an implicit close (by fully reading the resultset) while closeOnCompletion is true, will close
     * the statement.
     * <p>
     * JDBC 4.1 feature
     * </p>
     *
     * @throws SQLException
     */
    @Test
    public void testCloseOnCompletion_StatementClosed_afterImplicitResultSetClose() throws SQLException {
        FBPreparedStatement stmt = (FBPreparedStatement) con.prepareStatement(SELECT_DATA);
        try {
            stmt.closeOnCompletion();
            stmt.execute();
            // Cast so it also works under JDBC 3.0
            FBResultSet rs = (FBResultSet) stmt.getResultSet();
            int count = 0;
            while (rs.next()) {
                assertFalse("Resultset should be open", rs.isClosed());
                assertFalse("Statement should be open", stmt.isClosed());
                assertEquals(count, rs.getInt(1));
                count++;
            }
            assertEquals(DATA_ITEMS, count);
            assertTrue("Resultset should be closed (automatically closed after last result read)", rs.isClosed());
            assertTrue("Statement should be closed", stmt.isClosed());
        } finally {
            stmt.close();
        }
    }

    /**
     * Tests insertion of a single character into a single character field on a UTF8 connection.
     * <p>
     * See JDBC-234 for rationale of this test.
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void testInsertSingleCharOnUTF8() throws Exception {
        Properties props = getDefaultPropertiesForConnection();
        props.setProperty("lc_ctype", "UTF8");
        Connection connection = DriverManager.getConnection(getUrl(), props);

        try {
            final int id = 1;
            final String testString = "\u27F0"; // using high unicode character
            PreparedStatement pstmt = connection.prepareStatement("INSERT INTO testtab (id, field1, UTFFIELD) values (?, '01234567', ?)");
            try {
                pstmt.setInt(1, id);
                pstmt.setString(2, testString);
                pstmt.executeUpdate();
            } finally {
                pstmt.close();
            }

            PreparedStatement pstmt2 = connection.prepareStatement("SELECT UTFFIELD FROM testtab WHERE id = ?");
            try {
                pstmt2.setInt(1, id);
                ResultSet rs = pstmt2.executeQuery();

                assertTrue("Expected a row", rs.next());
                assertEquals("Unexpected value", testString, rs.getString(1));
            } finally {
                pstmt2.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * Tests if a parameter with a CAST around it will correctly be NULL when set
     * <p>
     * See JDBC-271 for rationale of this test.
     * </p>
     *
     * @throws Exception
     */
    @Test
    public void testNullParameterWithCast() throws Exception {
        PreparedStatement stmt = con.prepareStatement("SELECT CAST(? AS VARCHAR(1)) FROM RDB$DATABASE");
        try {
            stmt.setObject(1, null);
            ResultSet rs = stmt.executeQuery();
            assertTrue("Expected a row", rs.next());
            assertNull("Expected column to have NULL value", rs.getString(1));
            rs.close();
        } finally {
            closeQuietly(stmt);
        }
    }

    /**
     * Tests multiple batch executions in a row when using blobs created from a stream
     * <p>
     * See <a href="http://tracker.firebirdsql.org/browse/JDBC-312">JDBC-312</a>
     * </p>
     */
    @Test
    public void testRepeatedBatchExecutionWithBlobFromStream() throws Exception {
        con.setAutoCommit(false);
        List<byte[]> expectedData = new ArrayList<byte[]>();
        try {
            PreparedStatement insert = con.prepareStatement("INSERT INTO test_blob (id, obj_data) VALUES (?,?)");
            // Execute two separate batches inserting a random blob
            try {
                for (int i = 0; i < 2; i++) {
                    byte[] testData = new byte[50];
                    rnd.nextBytes(testData);
                    expectedData.add(testData.clone());
                    insert.setInt(1, i);
                    InputStream in = new ByteArrayInputStream(testData);
                    insert.setBinaryStream(2, in, testData.length);
                    insert.addBatch();
                    insert.executeBatch();
                }
            } finally {
                closeQuietly(insert);
            }

            // Check if the stored data matches the retrieved data
            Statement select = con.createStatement();
            try {
                ResultSet rs = select.executeQuery("SELECT id, obj_data FROM test_blob ORDER BY id");
                try {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        int id = rs.getInt(1);
                        byte[] data = rs.getBytes(2);

                        assertArrayEquals(String.format("Unexpected blob data for id %d", id), expectedData.get(id), data);
                    }
                    assertEquals("Unexpected number of blobs in table", 2, count);
                } finally {
                    closeQuietly(rs);
                }
            } finally {
                closeQuietly(select);
            }
        } finally {
            con.setAutoCommit(true);
        }
    }

    // Other closeOnCompletion behavior considered to be sufficiently tested in TestFBStatement

    private void prepareTestData() throws SQLException {
        PreparedStatement pstmt = con.prepareStatement(INSERT_DATA);
        try {
            for (int i = 0; i < DATA_ITEMS; i++) {
                pstmt.setInt(1, i);
                pstmt.executeUpdate();
            }
        } finally {
            pstmt.close();
        }
    }
}
