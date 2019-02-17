package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.content.Context;



/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 *
 * Please read:
 *
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 *
 * before you start to get yourself familiarized with ContentProvider.
 *
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 *
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         *
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */

        /*
         * First, in order to insert a new row of (key, value) pair, it is critical to obtain the key and value from the ContentValues (which stores values that ContentResolver can process). https://developer.android.com/reference/android/content/ContentValues.html
         * The ContentValues has multiple methods for extraction of different types of values, such as boolean, float, byte etc.
         * Since the project description (point 3.3) suggests that the all keys and values pair by provider are strings, we can use getAsString(). https://developer.android.com/reference/android/content/ContentValues.html#getAsString(java.lang.String)
         */
        String key = values.getAsString("key");
        String key_value = values.getAsString("value");

        /*
         * Due to the lack of current knowledge of SQLite, the same method is followed from PA1 where a file is created and stored in AVD's internal storage
         * https://developer.android.com/guide/topics/data/data-storage.html
         * The openFileOutput() is defined in the context class. Therefore, it needs to be called using getContext().
         * The BufferedWriter and OutputStreamWriter is used similar to PA1.
         */
        try {
            /*
             * Inverse to the InputStreamReader which reads the message, OutputStreamWriter can be used to write the message
             * OutputStream is superclass representing output stream of bytes (https://developer.android.com/reference/java/io/OutputStream.html)
             * OutputStreamWriter bridge from character streams to bytes (https://developer.android.com/reference/java/io/OutputStreamWriter.html)
            */
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(getContext().openFileOutput(key, Context.MODE_PRIVATE)));

            /*
             * writes the message to be send (https://developer.android.com/reference/java/io/BufferedWriter.html)
            */
            output.write(key_value);

            output.close();

            /*
             * Another way is to use FileOutputStream class.
             * The FileOutputStream is used to access the key and write its corresponding value.
             * The BufferedWriter is used for more efficiency and consistency (with PA1).
             */
            //FileOutputStream output_stream;
            //output_stream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            //output_stream.write(key_value.getBytes());
            //output_stream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */

        /*
         * Since the internal storage option is used, MatrixCursor needs to be built.
         * Its documentation suggests that the argument required are array of string which denotes columns.
         * The array of string, in our case, only requires to contain key and value.
         */
        String[] column_names = {"key", "value"};
        MatrixCursor matrix_cursor;
        matrix_cursor = new MatrixCursor(column_names);

        /*
         * The String selection provides the key to be read as a parameter.
         */
        try {
            /*
             * InputStream is superclass for all classes representing an input stream of bytes to be used with a socket. (https://developer.android.com/reference/java/net/Socket.html, https://developer.android.com/reference/java/io/InputStream.html)
             * InputStreamReader is used to bridge from byte streams to characters. While the connection is live, the InputStreamReader is used to read one or more bytes from byte-stream. (https://developer.android.com/reference/java/io/InputStreamReader.html)
             * BufferedReader is used to increase efficiency/performance with the InputStreamReader as it buffers the data into the memory for quick access. Use of this is optional (https://developer.android.com/reference/java/io/InputStreamReader.html)
             * Additional source for understanding concepts to determine the relation between above three is http://www.javalearningacademy.com/streams-in-java-concepts-and-usage/
             */
            BufferedReader input = new BufferedReader(new InputStreamReader(getContext().openFileInput(selection)));

            /*
             * readLine() is used to read a line of text and obtain the value to pass on.
             */
            String message = input.readLine();

            /*
             * The rows for the matrix cursor will include information of key (from selection) and value (from message).
             */
            String[] column_contents = {selection, message};
            Log.v("query", selection.toString() + " " + message.toString());
            /*
             * A new row is added to the matrix cursor in order to maintain a record of (key,value) pairs.
             */
            matrix_cursor.addRow(column_contents);

            input.close();
            return matrix_cursor;

            /*
             * If FileOutputStream was used for insert, then FileInputStream can be used to read.
             * To maintain consistency with PA1, InputStreamReader is used.
             */
            //FileInputStream input_stream;
            //input_stream = getContext().openFileInput(String.valueOf(selection));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.v("query", selection);
        return null;
    }
}