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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

import org.firebirdsql.gds.XSQLVAR;


/**
 * This is Blob-based implementation of {@link FBStringField}. It should be used
 * for fields declared in database as <code>BLOB SUB_TYPE 1</code>. This 
 * implementation provides all conversion routines {@link FBStringField} has.
 * 
 * @author <a href="mailto:rrokytskyy@users.sourceforge.net">Roman Rokytskyy</a>
 * @version 1.0
 */
public class FBLongVarCharField extends FBStringField implements FBFlushableField{

    private static final int BUFF_SIZE = 4096;
    
    private boolean isCachedData = false;
    
    private FBBlob blob;

    // Rather then hold cached data in the XSQLDAVar we will hold it in here.
    int length;
    byte[] data;

    FBLongVarCharField(XSQLVAR field, FBResultSet rs, int numCol) throws SQLException {
        super(field, rs, numCol);
    }
    
    public void close() throws SQLException {
        try {
            if (blob != null) 
                blob.close();
        } catch(IOException ioex) {
            throw new FBSQLException(ioex);
        } finally {       
            // forget this blob instance, resource waste
            // but simplifies our life. BLOB handle will be
            // released by a server automatically later

            blob = null;
        }
    }
    
    Blob getBlob() throws SQLException {
        
        if (blob != null)
            return blob;
        
    /*
    // commented out by R.Rokytskyy since getBlob(boolean) is
    // used only from getBlob() and it makes sense to join two
    // methods
        return getBlob(false);
    }

    Blob getBlob(boolean create) throws SQLException {
    */
        if (rs.row[numCol]==null)
            return BLOB_NULL_VALUE;

        Long blobId = new Long(field.decodeLong(rs.row[numCol]));
        
        /*
        // commented out by R.Rokytskyy, it's dead code
        if (blobId == null)
            blobId = new Long(0);
        */

        blob = new FBBlob(c, blobId.longValue());
        return blob;
    }
    
    InputStream getBinaryStream() throws SQLException {
        Blob blob = getBlob();

        if (blob == BLOB_NULL_VALUE)
            return STREAM_NULL_VALUE;

        return blob.getBinaryStream();
    }
    
    byte[] getBytes() throws SQLException {

        Blob blob = getBlob();

        if (blob == BLOB_NULL_VALUE)
            return BYTES_NULL_VALUE;

        InputStream in = blob.getBinaryStream();

        if (in == STREAM_NULL_VALUE)
            return BYTES_NULL_VALUE;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        // copy stream data
        byte[] buff = new byte[BUFF_SIZE];
        int counter = 0;
        try {
            while((counter = in.read(buff)) != -1) {
                bout.write(buff, 0, counter);
            }
        } catch(IOException ioex) {
            throw (SQLException)createException(
                BYTES_CONVERSION_ERROR + " " + ioex.getMessage());
        } finally {
            try {
                in.close();
            } catch(IOException ioex) {
                throw new SQLException("Unable to close BLOB input stream.");
            }

            try {
                bout.close();
            } catch(IOException ioex) {
                throw new SQLException("Unable to close ByteArrayOutputStream.");
            }
        }

        return bout.toByteArray();
    }

    Object getObject() throws SQLException {
        return getString();
    }
    
    public byte[] getCachedObject() throws SQLException {
        if (rs.row[numCol]==null) 
            return BYTES_NULL_VALUE;

          return getBytes();
    }

    String getString() throws SQLException {
        byte[] data = getBytes();
        
        if (data == BYTES_NULL_VALUE)
            return STRING_NULL_VALUE;
        
        return field.decodeString(data, javaEncoding);
    }

    void setString(String value) throws SQLException {
        
        if (value == STRING_NULL_VALUE) {
            setNull();
            return;
        }
        
        byte[] data = field.encodeString(value, javaEncoding);
        setBinaryStream(new ByteArrayInputStream(data), data.length);
    }

    void setBytes(byte[] value) throws SQLException {

        if (value == BYTES_NULL_VALUE) {
            setNull();
            return;
        }

        byte[] data = field.encodeString(value, javaEncoding);
        setBinaryStream(new ByteArrayInputStream(data), data.length);
    }

    private void copyBinaryStream(InputStream in, int length) throws SQLException {
        
        /** @todo check if this is correct!!! */
        if (!c.getAutoCommit())
            c.ensureInTransaction();
        
        FBBlob blob =  new FBBlob(c, 0);
        blob.copyStream(in, length);
        field.sqldata = field.encodeLong(blob.getBlobId());
    }

    void setBinaryStream(InputStream in, int length) throws SQLException {
        
        if (in == STREAM_NULL_VALUE) {
            setNull();
            return;
        }
        
        if (!c.getAutoCommit()) {
            copyBinaryStream(in, length);
        } else {
            byte[] buff = new byte[BUFF_SIZE];
            ByteArrayOutputStream bout = new ByteArrayOutputStream(length);

            int chunk;
            try {
                while (length >0) {
                    chunk =in.read(buff, 0, ((length<BUFF_SIZE) ? length:BUFF_SIZE));
                    bout.write(buff, 0, chunk);
                    length -= chunk;
                }
                bout.close();
            }
            catch (IOException ioe) {
                throw new SQLException("read/write blob problem: " + ioe);
            }

            this.data = bout.toByteArray();
            this.length = data.length;
            isCachedData = true;
        }
    }

    public void flushCachedData() throws SQLException {
        if (isCachedData){
            copyBinaryStream(
                new ByteArrayInputStream(data), length);
            isCachedData=false;
        }
    }

}