/*
 * Firebird Open Source J2ee connector - jdbc driver
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
 * can be obtained from a CVS history command.
 *
 * All rights reserved.
 */
package org.firebirdsql.jdbc;

import org.firebirdsql.gds.ng.fields.FieldValue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * This interface defines set of listeners that will be called in different situations.
 *
 * @author <a href="mailto:rrokytskyy@users.sourceforge.net">Roman Rokytskyy</a>
 * @author <a href="mailto:mrotteveel@users.sourceforge.net">Mark Rotteveel</a>
 */
public interface FBObjectListener {

    interface FetcherListener {

        /**
         * Notify listener that underlying fetcher is closed.
         *
         * @param fetcher
         *         fetcher that was closed.
         */
        void fetcherClosed(FBFetcher fetcher) throws SQLException;

        /**
         * Notify listener that underlying fetcher fetched all rows.
         *
         * @param fetcher
         *         fetcher that fetched all rows.
         */
        void allRowsFetched(FBFetcher fetcher) throws SQLException;

        /**
         * Notify listener that underlying row was changed.
         *
         * @param fetcher
         *         instance of {@link FBFetcher} that caused this event.
         * @param newRow
         *         new row.
         */
        void rowChanged(FBFetcher fetcher, List<FieldValue> newRow) throws SQLException;
    }

    /**
     * Listener for the events generated by the result set.
     */
    interface ResultSetListener {

        /**
         * Notify listener that result set was closed.
         *
         * @param rs
         *         result set that was closed.
         */
        void resultSetClosed(ResultSet rs) throws SQLException;

        /**
         * Notify listener that all rows were fetched. This event is used in auto-commit case to tell the statement that
         * it is completed.
         *
         * @param rs
         *         result set that was completed.
         */
        void allRowsFetched(ResultSet rs) throws SQLException;

        /**
         * Notify listener that execution of some row updating operation started.
         *
         * @param updater
         *         instance of {@link FirebirdRowUpdater}
         * @throws SQLException
         *         if somewthing went wrong.
         */
        void executionStarted(FirebirdRowUpdater updater) throws SQLException;

        /**
         * Notify listener that execution of some row updating operation is completed.
         *
         * @param updater
         *         instance of {@link FirebirdRowUpdater}.
         * @throws SQLException
         *         if something went wrong.
         */
        void executionCompleted(FirebirdRowUpdater updater, boolean success) throws SQLException;
    }

    /**
     * Implementation of {@link org.firebirdsql.jdbc.FBObjectListener.ResultSetListener} that implements all methods as
     * empty methods.
     */
    final class NoActionResultSetListener implements ResultSetListener {

        private static final ResultSetListener INSTANCE = new NoActionResultSetListener();

        public static ResultSetListener instance() {
            return INSTANCE;
        }

        private NoActionResultSetListener() {
        }

        @Override
        public void resultSetClosed(ResultSet rs) throws SQLException { }
        @Override
        public void allRowsFetched(ResultSet rs) throws SQLException { }
        @Override
        public void executionStarted(FirebirdRowUpdater updater) throws SQLException { }
        @Override
        public void executionCompleted(FirebirdRowUpdater updater, boolean success) throws SQLException { }
    }

    /**
     * Listener for the events generated by statements.
     */
    interface StatementListener {

        /**
         * Get the connection object to which this listener belongs to.
         *
         * @return instance of {@link FBConnection}
         * @throws SQLException
         *         if something went wrong.
         */
        FBConnection getConnection() throws SQLException;

        /**
         * Notify listener that statement execution is being started.
         *
         * @param stmt
         *         statement that is being executed.
         * @throws SQLException
         *         if something went wrong.
         */
        void executionStarted(FBStatement stmt) throws SQLException;

        /**
         * Notify the listener that statement was closed.
         *
         * @param stmt
         *         statement that was closed.
         */
        void statementClosed(FBStatement stmt) throws SQLException;

        /**
         * Notify the listener that statement is completed. This is shortcut method for
         * <code>statementCompleted(AbstractStatement, true)</code>.
         *
         * @param stmt
         *         statement that was completed.
         */
        void statementCompleted(FBStatement stmt) throws SQLException;

        /**
         * Notify the listener that statement is completed and tell whether execution was successfull or not.
         *
         * @param stmt
         *         statement that was completed.
         * @param success
         *         <code>true</code> if completion was successfull.
         * @throws SQLException
         *         if an error occured.
         */
        void statementCompleted(FBStatement stmt, boolean success)
                throws SQLException;
    }

    /**
     * Listener for the events generated by BLOBs.
     */
    interface BlobListener {

        /**
         * Notify listener that execution of some BLOB operation had been started.
         *
         * @param blob
         *         instance of {@link FirebirdBlob} that caused this event.
         * @throws SQLException
         *         if something went wrong.
         */
        void executionStarted(FirebirdBlob blob) throws SQLException;

        /**
         * Notify listener that execution of some BLOB operation had been completed.
         *
         * @param blob
         *         instance of {@link FirebirdBlob} that caused this event.
         * @throws SQLException
         *         if something went wrong.
         */
        void executionCompleted(FirebirdBlob blob) throws SQLException;
    }

    final class NoActionBlobListener implements BlobListener {

        private static final BlobListener INSTANCE = new NoActionBlobListener();

        public static BlobListener instance() {
            return INSTANCE;
        }

        private NoActionBlobListener() {
        }

        @Override
        public void executionStarted(FirebirdBlob blob) throws SQLException { }
        @Override
        public void executionCompleted(FirebirdBlob blob) throws SQLException { }
    }

}