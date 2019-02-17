package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.telephony.TelephonyManager;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Timer;

import android.content.Context;
import android.os.AsyncTask;
/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity{

    static final String TAG = GroupMessengerActivity.class.getSimpleName();

    /*
     * The app listens to one server socket on port 10000.
     */
    static final int SERVER_PORT = 10000;

    /*
     * Each emulator has a specific remote port it should connect to.
     */
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";


    /*
     * A global sequence number is used for keeping count of the largest proposed priority.
     * A local sequence number is used for individual delivered messages and can also be interpreted as key in the key-value pair.
     */
    public static int global_sequence_number = 0;
    static int local_sequence_number = 0;

    //------------------------------------------
    // Some additional variables that were used but not needed after changes.
    //int[] priorities = new int[5];
    //static int index = 0;
    //------------------------------------------

    /*
     * Lets first write the necessities for implementing the ISIS algorithm.
     * 1. Implement a B-multicast function (we already did this in PA2A).
     * 2. Each process has a proposed priority (sequence number). Typically local
     * 3. Priority queue to store messages. Each message are ordered by priority (proposed or agreed).
     * 4. Each message should have a state, deliverable or undeliverable.
     * 5. An agreed priority which is maximum of all proposed priorities.
     * 6. When agreed priority is received, mark the message as deliverable and place it in from of priority queue.
     */

    //------------------------------------------
    // A HashMap was intended for keeping track of the messages with their priorities but was not required.
    // Similarly, additional priority queue was intended to used for having two queues keeping track of undeliverable and deliverable messages.
    // However, during the temp queue implementation the messages were not stored properly so a solution on using port number was used instead of revising this.
    //HashMap<Integer, MessageHandler> priorities = new HashMap<Integer, MessageHandler>();
    //PriorityQueue<MessageHandler> temp = new PriorityQueue<MessageHandler>();
    //------------------------------------------

    /*
     * The message_queue is using the specified handler to keep track of deliverable and undeliverable messages.
     * MessageHandler is one of the most important component since it decide the order of message while storing in queue.
     * Sequence_number_list can be used for keeping track of agreed priorities for debugging purposes.
     * Port_used corresponds to the individual ports through which the messages are sent. This will be used for comparing two handlers when their sequence number are equal.
     * The port solution, suggested by professor Ethan Blanton, was found to be much better than the others (that I couldn't get to work but are still mentioned in this file as commented code).
     */
    PriorityQueue<MessageHandler> message_queue = new PriorityQueue<MessageHandler>();
    MessageHandler message_handler = new MessageHandler();
    ArrayList<Integer> sequence_number_list = new ArrayList<Integer>();
    static String port_used = "";
    ArrayList<String> failed_avd = new ArrayList<String>();
    int failed_port = 0;
    static boolean checkpoint = false;
    /*
     * A string variable to keep track of the error port. Since in the implementation ports are in string format for socket creation, that is why the type of this variable is also string.
     */
    String error_port = "0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * Calculates the port number this AVD listen on.
         * Taken from PA1.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        port_used = myPort;
        Log.e(TAG, "Port: " + myPort);

        /*
         * A server socket needs to be created, in addition to a thread (AsyncTask), that listens on the server port.
         * PA1 code can be taken as a skeleton.
         */
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a server socket");
            e.printStackTrace();
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

         /*
         * To acquire the access to the pointer to the input region where user enters the message to be sent.
         * Taken from PA2A.
         */
        final EditText edit_text = (EditText) findViewById(R.id.editText1);

        /*
         * The send button is used to deliver the message inputted from the edit_text.
         * Taken from PA2A.
         */
        Button send_button = (Button) findViewById(R.id.button4);

        /*
         * To configure the send button to show the message input by the user, setOnClickListener is used.
         * If the software keyboard is used, then setOnKeyListener should be used to listen for the enter key.
         * However, the updateavd.py was run for using hardware keyboard in the emulators.
         */
        send_button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {

                /*
                 * PA2A explains the code that can be used to retrieve the message and display it.
                 * However, before sending the message over the network, the id of the message in MessageHandler needs to be updated to account for each new message.
                 * Each message that is retrieved will have the initial string, id and port that sent the message. This will be handled by the ClientTask.
                 */
                message_handler.message_id = message_handler.message_id + 1;
                String message = edit_text.getText().toString() + ";" + message_handler.message_id + ";" + port_used;
                /*
                 * To reset the input box.
                 */
                edit_text.setText("");
                TextView tv = (TextView) findViewById(R.id.textView1);

                //------------------------------------------
                //To display the message (string). However, this lead to printing out the sending message twice on sender's UI.
                //tv.append(message + "\t");
                //tv.append("\n");
                //------------------------------------------

                /*
                 * To send the string over network.
                 * A Thread_Pool_Executor is used instead of the Serial_Executor since the aim is to let individual threads execute task in parallel.
                 */
                new ClientTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message, myPort);
            }
        });

    }

    /*
     * ServerTask is created to handle the incoming messages.
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... serverSockets) {

            /*
             * The overall skeleton is taken from PA2A, however additional functionalities are defined.
             */
            ServerSocket serverSocket = serverSockets[0];

            //------------------------------------------
            // Additional variables which can be ignored.
            //Socket client = null;
            //String incoming_message;
            //String message;
            //------------------------------------------

            Log.e(TAG, "It reaches inside ServerTask");

            /*
             * do-while is used to make sure that continous message can be exchanged until the read-half of the socket connection is open which is checked by isInputShutDown (https://developer.android.com/reference/java/net/Socket.html)
             * If not used, only one message can be send between the avds, similar to PA1.
             */
            try {
                Log.e(TAG, "It reaches inside the try phrase in ServerTask");
                do {
                    /*
                     * Socket is an endpoint for communication between two machines and underlying class implements CLIENT sockets. (https://developer.android.com/reference/java/net/Socket.html)
                     * The serverSocket waits for requests to come in over the network and underlying class implements SERVER sockets. (https://developer.android.com/reference/java/net/ServerSocket.html)
                     * Once serverSocket detects the incoming connection, it is first required to accept it and for communication, create a new instance of socket.
                     * The accept() method listens for a connection to be made to this socket and accepts it. (https://developer.android.com/reference/java/net/ServerSocket.html#accept())
                     */
                    Socket socket = serverSocket.accept();

                    Log.e(TAG, "In the serverSocket loop inside the ServerTask");

                    /*
                     * Note: Initially the idea was to maintain as much consistency as possible with PA2A and use the BufferedReaders/Writers. Also, it was found that other stream readers doesn't work properly during the testing due to the functionality required (further defined).
                     * InputStream is superclass for all classes representing an input stream of bytes to be used with a socket. (https://developer.android.com/reference/java/net/Socket.html, https://developer.android.com/reference/java/io/InputStream.html)
                     * InputStreamReader is used to bridge from byte streams to characters. While the connection is live, the InputStreamReader is used to read one or more bytes from byte-stream. (https://developer.android.com/reference/java/io/InputStreamReader.html)
                     * BufferedReader is used to increase efficiency/performance with the InputStreamReader as it buffers the data into the memory for quick access. Use of this is optional (https://developer.android.com/reference/java/io/InputStreamReader.html)
                     * Additional source for understanding concepts to determine the relation between above three is http://www.javalearningacademy.com/streams-in-java-concepts-and-usage/
                     */
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    //------------------------------------------
                    // A problem was observed. The input.readline either blocked the code or resulted in giving NullPointerException.
                    // This was primarily because of socket connections that weren't operated properly resulting in a reader waiting for a message from a socket that has been abruptly closed.
                    // Just waiting for the input wasn't working so after a LOT of testing with the sockets, the current format of its creating and closing was decided.
                    //if(!input.ready()){
                    //    wait(500);
                    //}
                    //Log.e(TAG, String.valueOf(client.isInputShutdown()));
                    //Log.e(TAG, "Step 2: The input stream is prepared using buffered reader");
                    //Log.e(TAG, "G:" + String.valueOf(input.readLine()==null));
                    //------------------------------------------

                    socket.setSoTimeout(1000);
//                    if (socket.getInputStream()==null){
//                        Log.e(TAG,"InputStream is null");
//                        throw new Exception();
//                    }
                    /*
                     * readLine() is used to read a line of text and obtain the message to pass on.
                     * However, unlike PA2A, the incoming message is comprising of message, status, sequence number, port and id.
                     * Therefore, the message is split to retrieve individual information.
                     * It was observed that the readline was blocking the code, and waiting for end of the line which is never observed.
                     * This was handled using proper socket creation and closing and making sure that the incoming message is ending with a new line character.
                     */
                    String[] message_segments = input.readLine().split(";");

                    /*
                     * The messages that come from the error port will be empty or null.
                     * In that case the error port can be determined by checking the 4 segment of the overall message.
                      * After that an exception is thrown.
                     */
                    if (message_segments[0].isEmpty()==true || message_segments[0]==null){
                        Log.e(TAG, "The message is of length 0 or null");
                        error_port = message_segments[3];
                        throw new Exception();
                    }
                    Log.e(TAG, "Step 2: The readline (message_segements) is successful for receiving the initial message");

                    /*
                     * The message that is initially multicasted is retrieved with all the contained information.
                     * A MessageHandler with local scope is used for individual process to send the proposed priority later on.
                     */
                    MessageHandler local_handler = new MessageHandler();
                    local_handler.message = message_segments[0];
                    local_handler.message_status = message_segments[1];
                    local_handler.sequence_number = Integer.parseInt(message_segments[2]);;
                    local_handler.port = Integer.parseInt(message_segments[3]);
                    local_handler.message_id = Integer.parseInt(message_segments[4]);

                    Log.d(TAG, "message:" + message_segments[0] + "; message status:" + message_segments[1] + "; sequence number:" + local_handler.sequence_number + "; port:" + local_handler.port + "; message id:" + local_handler.message_id);

                    /*
                     * The messages are intented to be stored in the priority queue.
                     * The messages that are marked undeliverable can be just added to the end of the queue.
                     * The sequence number is equal to the global sequence number which takes into account the largest priority observed.
                     */
                    if(local_handler.message_status.equals("undeliverable")){

                        Log.d(TAG, "It reaches here!!!!");
                        //agreed_sequence_number = global_seq_number;
                        local_handler.sequence_number = global_sequence_number;
                        /*
                         * A synchronized command is used to ensure that when one thread is adding to the queue, no other threads can access it.
                         * It was recommended by professor ethan blanton to use one of these methods.
                         */
                        synchronized (message_queue){
                            message_queue.add(local_handler);
                        }

                        Log.e(TAG, "Step 3: The queue is updated: " + message_queue.size());
                        /*
                         * The sequence number proposals need to be updated at the corresponding process.
                         * After the proposal is sent, then the global sequence number is updated.
                         * In addition, the message id is also sent to ensure the correspondance with the sequence number.
                         */
                        String message_to_send = local_handler.message_id+";"+Integer.toString(global_sequence_number);
                        global_sequence_number++;

                        //------------------------------------------
                        // Just some additional code.
                        //Log.e(TAG, "The message is convered to string: " +  message_to_send);
                        //index = message_handler.message_id;
                        //output.write(message_to_send + "\n");
                        //output.flush();
                        //------------------------------------------

                        /*
                         * Inverse to the InputStreamReader which reads the message, OutputStreamWriter can be used to write the message.
                         * OutputStream is superclass representing output stream of bytes (https://developer.android.com/reference/java/io/OutputStream.html).
                         * OutputStreamWriter bridge from character streams to bytes (https://developer.android.com/reference/java/io/OutputStreamWriter.html).
                         */
                            socket.setSoTimeout(1000);
                            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                            output.write(message_to_send + "\n");
                            output.flush();

                        //------------------------------------------
                        //The primary aim was to use the DataOutputStream in coherance with implemeting DataInputStream in ClientTask for sending the id and seq number.
                        //However, the DataStreams resulted in W/System.err: java.io.FileNotFoundException: /data/data/edu.buffalo.cse.cse486586.groupmessenger2/files/24: open failed: ENOENT (No such file or directory).
                        //Therefore, the BufferedWriter was used as stated above.
                        //DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                        //output.writeUTF(message_to_send + "\n");
                        //output.flush();
                        //------------------------------------------

                        Log.e(TAG, "Step 2: Message sent with updated sequence number proposal: " + message_to_send);
                    }
                    /*
                     * Upon receiving the agreed priority, the messages that are marked "deliverable" are need to be delivered from the front of the priority queue.
                     */
                    else{
                        /*
                         * For all the messages that are marked delivered..
                         */
                        //if(local_handler.port != Integer.parseInt(error_port)) {
                            Log.e(TAG, "Step 5: The messages are ready to be delivered");
                            Log.e(TAG, "Queue size: " + message_queue.size());

                            Log.e(TAG, "The queue contains handler? Ans: " + message_queue.contains(local_handler));
                            Log.e(TAG, "The queue contains: " + message_queue.peek().message + message_queue.peek().sequence_number + message_queue.peek().message_id + message_queue.peek().message_status);
                            Log.e(TAG, "Local handler: " + local_handler.message + local_handler.sequence_number + local_handler.message_id + local_handler.message_status);

                        /*
                         * The previously needed message handler needs to be replaced in the queue otherwise the status will remain "undeliverable".
                         * This results in the Missing key error when running the testing script.
                         */
                            modifyQueue(local_handler);
                        /*
                         * Once the messages are ready, deliver them from the front of the queue till one reaches the undeliverable ones.
                         */
                            deliverMessage();
                            //Log.d(TAG, "It reaches here!!!!");
                         /*
                         * The global sequence number needs to updated.
                         * This step is performed at the end since the sequence number were initialized with 0 as the start of the code.
                         * The global sequence number is the largest of the all the observed agreed priorities.
                         */
                            global_sequence_number = Math.max(global_sequence_number, local_handler.sequence_number) + 1;
                        //}
                    }

                    Log.e(TAG, "outside the serverSocket loop, message inside handler: " + local_handler.message);
                    socket.close();

                } while(true);
            } catch (SocketTimeoutException e){
                manageQueue();
            } catch (IOException e){
                Log.e(TAG, "IOException server side");
                manageQueue();
//                if(error_port.equals("11124")){
//                    doInBackground(serverSockets);
//                }
            } catch (Exception e) {
                Log.e(TAG, "Exception server side");
                //e.printStackTrace();
                //doInBackground(serverSockets);
                manageQueue();
                //The initial thought was that the serverTask needs to be run again after the removal but this was proved wrong during the testing.
                //doInBackground(serverSockets);
//                if(error_port.equals("11124")){
//                    doInBackground(serverSockets);
//                }
            }
            return null;
        }

        private void modifyQueue(MessageHandler local_handler){

            //------------------------------------------
            //This was the initial code used for checking the if the queue contains the message already and if so remove it to add the updated one.
            //However, it was observed that the condition of comparing the process_id with elements on the queue and the message would result in always been equal since the message handler was initially declared global. Some other errors were also observed.
            //Therefore, this way of updating the queue is not correct and was further updated with a more simplistic version.
            //temp = message_queue;
            //for (int i = 0; i < message_queue.size(); i++) {
            //    MessageHandler temp_handler = message_queue.poll();
            //    Log.e(TAG,"The queue element: " + temp_handler.message + "Sequence number: " + temp_handler.sequence_number);
            //    if (message_queue.peek().process_id == message_handler.process_id) {
            //         synchronized (message_queue) {
            //             Log.e(TAG, "The messages are equal!!");
            //             message_queue.poll();
            //             message_queue.add(message_handler);
            //         }
            //    }
            //}
            //Log.e(TAG, "Queue size: " + message_queue.size());
            //------------------------------------------

            /*
             * For updating the queue. (https://developer.android.com/reference/java/util/PriorityQueue.html)
             */
            if(message_queue.contains(local_handler)){
                synchronized (message_queue){
                    Log.e(TAG, "It doesn't reach here?");
                    message_queue.remove(local_handler);
                    message_queue.add(local_handler);
                }
            }
        }

        /*
         * This function is used for delivering the messages that are marked deliverable.
         */
        private void deliverMessage(){
            /*
             * The queue needs to be searched continuously to access all the messages that are deliverable.
             */
            while (message_queue.size() != 0) {
                synchronized(message_queue){
                    //message_queue.peek().message_status = "deliverable";
                    /*
                     * The first message that is needed to be delivered is acquried from the front of the queue.
                     * A solution using iterator isn't advised since the queue itself would be changing everytime.
                     */
                    MessageHandler deliver_message = message_queue.peek();
                    Log.e(TAG, "Deliver message: " + message_queue.peek().message_status);
                    /*
                     * To only execute it when messages are marked deliverable.
                     */
                    if (deliver_message.message_status.equals("deliverable")) {
                        /*
                         * Remove the message from the queue and send it as a key-value pair.
                         */
                        deliver_message = message_queue.poll();
                        String[] final_message = {deliver_message.message, Integer.toString(local_sequence_number)};
                       /*
                        * It is required that the incoming message should be passed onto the onProgressUpdate.
                        * However, if onProgressUpdate(message) is called directly, it results in Fatal Exception AsyncTask1 (observed in PA1) while executing doInBackground() caused by android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
                        * Android documentation on AsyncTask suggests that onProgressUpdate runs on the UI thread after PublishProgress.
                        * PublishProgress can be invoked in doInBackground to publish updates on UI thread and triggers the execution of onProgressUpdate, necessary for our task (https://developer.android.com/reference/android/os/AsyncTask.html#publishProgress(Progress...))
                        */
                        publishProgress(final_message);
                        /*
                         * The local sequence number corresponding to the key number is updated after each message is delivered.
                         */
                        local_sequence_number++;
                    } else {
                        break;
                    }
                }
            }

        }
        /*
         * To build a URI for content provider. Referred from OnPTestClickListener.
         */
        private Uri buildUri(String content, String s) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(s);
            uriBuilder.scheme(content);
            return uriBuilder.build();
        }

        protected void onProgressUpdate(String...strings) {

            final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

            /*
             * To insert the key-value pair.
             * The code is given in the PA2A description.
             * The key will be the respective number of the message, while the value will be the message itself.
             * It is important to convert the message number (int) to string since all key-value should be Java strings.
             * Note: The key-value pair needs to be updated in this method rather than ServerTask since we are ordering the message before finally publishing them.
             */
            String message = strings[0];
            String message_sequence = strings[1];

            ContentValues pair_to_insert = new ContentValues();

            pair_to_insert.put("key", message_sequence);
            pair_to_insert.put("value", message);
            Uri new_uri = getContentResolver().insert(mUri, pair_to_insert);

        /*
         * The following code, obtained from PA1, displays the content received in doInBackground().
         */
            String string_received = strings[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(string_received + "\t");
            tv.append("\n");
            return;
        }

    }

    /*
     * ClientTask is an AsyncTask that sends the message (in form of string) over the network.
     * It is created whenever the send button is pressed.
     */
    private class ClientTask extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... strings) {

            /*
             * The initial message that is multicasted (step 1) is determined to have sequence number of -1.
             */
            int process_sequence_number = -1;
            /*
             * The agreed priority sequence number will be initialized as 0.
             */
            int agreed_sequence_number = 0;
            /*
             * Since each process replies with a proposed priority, a counter is required to take in account all the process.
             * It makes sure that the sender multicasts the messages only after comparing all proposed priorities. (step 4)
             */
            int counter = 5;
            /*
             * All the initial messages are marked as undeliverable.
             */
            String message_status = "undeliverable";
            /*
             * The incoming message that is entered is splitted since it will contain information about the message, its id and port number.
             */
            String[] incoming_message = strings[0].split(";");
            String message = incoming_message[0];
            int incoming_message_id = Integer.parseInt(incoming_message[1]);
            //The process id is not used, instead the port number is used.
            int process_id = Integer.parseInt(strings[1]);
            int port = Integer.parseInt(incoming_message[2]);
            String temp = "0";
            /*
             * Each individual port will be connected to respective emulators.
             * The message received needs to be outputted across all emulators.
             */
            String[] ports = {"11108","11112","11116","11120","11124"};
            for(int i=0; i<ports.length; i++) {
                try {
                    /*
                     * A socket is created for multicasting.
                     */
                    String remote_port = ports[i];
                    temp = remote_port;
                    /*
                     * First, it is checked whether the port is one of failed ones before the socket is created.
                     */
                    if(temp.equals(error_port)!=true) {

                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remote_port));

                        Log.e(TAG, "Step 0: Input message by the user: " + message);
                        //------------------------------------------
                        //Since the documentation mentions not to rely on socket creation and connection status but also to use read timeout exceptions.
                        //Therefore, this one is combined with the input stream check to determine if the stream is null or not.
                        //if (socket.isConnected() != true){
                        //    failed_port = remote_port;
                        //    socket.setSoTimeout(500);
                        //    throw new Exception();
                        //}
                        //------------------------------------------
                    /*
                     * Each message that is to be multicasted will have the initial string, status, sequence number, port and id. All are seperated by semicolon (;) notation. (Step 1)
                     * The port number was added to resolve the case of "Different message under the same message sequence" as suggested by professor ethan blanton.
                     */
                        String new_message = message + ";" + message_status + ";" + Integer.toString(process_sequence_number) + ";" + port + ";" + Integer.toString(incoming_message_id);
                        Log.e(TAG, "Step 1: Initial message multicasted: " + new_message);

                    /*
                     * To check whether there is an output stream available for the message.
                     */
//                    if(socket.getOutputStream()==null){
//                        Log.e(TAG, "Output stream is null");
//                        throw new Exception();
//                    }
                        socket.setSoTimeout(1000);
                    /*
                     * Inverse to the InputStreamReader which reads the message, OutputStreamWriter can be used to write the message.
                     * OutputStream is superclass representing output stream of bytes (https://developer.android.com/reference/java/io/OutputStream.html).
                     * OutputStreamWriter bridge from character streams to bytes (https://developer.android.com/reference/java/io/OutputStreamWriter.html).
                     */
                        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        output.write(new_message + "\n");
                        output.flush();

//                    if(socket.getInputStream()==null || socket.isConnected()==false){
//                        failed_port = Integer.parseInt(remote_port);
//                        Log.e(TAG, "Before the timeout");
//                        socket.setSoTimeout(500);
//                        Log.e(TAG, "After the timeout.");
//                        throw new Exception();
//                    }
                        //socket.setSoTimeout(500);
                        if (socket.getInputStream() == null) {
                            Log.e(TAG, "The input stream is null");
                            throw new Exception();
                        }

                    /*
                     * Initially the idea was to maintain as much consistency as possible with PA2A and use the BufferedReaders/Writers. Also, it was found that other stream readers doesn't work properly during the testing due to the functionality required.
                     * InputStream is superclass for all classes representing an input stream of bytes to be used with a socket. (https://developer.android.com/reference/java/net/Socket.html, https://developer.android.com/reference/java/io/InputStream.html)
                     * InputStreamReader is used to bridge from byte streams to characters. While the connection is live, the InputStreamReader is used to read one or more bytes from byte-stream. (https://developer.android.com/reference/java/io/InputStreamReader.html)
                     * BufferedReader is used to increase efficiency/performance with the InputStreamReader as it buffers the data into the memory for quick access. Use of this is optional (https://developer.android.com/reference/java/io/InputStreamReader.html)
                     * Additional source for understanding concepts to determine the relation between above three is http://www.javalearningacademy.com/streams-in-java-concepts-and-usage/
                     */
                        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        socket.setSoTimeout(1000);
                        String[] reading_message = input.readLine().split(";");

                        //------------------------------------------
                        //As mentioned previously, using DataStreams causes error: W/System.err: java.io.FileNotFoundException: /data/data/edu.buffalo.cse.cse486586.groupmessenger2/files/24: open failed: ENOENT (No such file or directory)
                        //DataInputStream input = new DataInputStream(socket.getInputStream());
                        //String[] reading_message = input.readUTF().split(";");
                        //------------------------------------------

                        Log.e(TAG, "The input stream is used for getting the proposed sequence number.");

                        //------------------------------------------
                        //Also, InputStreamReader was also considered instead of BufferedReaders. However, since it only provides write method for integers, the idea was discarded as sending multiple integers and subtracting them by 48 was problematic.
                        //int proposed_sequence_number = 0;
                        //if(!input.ready()){
                        //    wait(500);
                        //}
                        //Log.e(TAG, "A:" + String.valueOf(input.readLine()==null));
                        //Log.e(TAG, "Step 4: The message with updated sequence number: " + Integer.parseInt(reading_message[0]) + "  " + Integer.parseInt(reading_message[1]));
                        //Log.e(TAG, String.valueOf(input.readLine()==null));
                        //int message_segmentss = input.read() - 48;
                        //Log.e(TAG,"Message: " + message_segmentss[0] + "Message size: " + message_segmentss.length);
                        //------------------------------------------

                    /*
                     * The input is read which contains the proposed sequence number for a message id.
                     */
                        int new_message_id = Integer.parseInt(reading_message[0]);
                        int proposed_sequence_number = Integer.parseInt(reading_message[1]);
                    /*
                     * Decreasing the counter everytime the input is read correctly for sequence numbers.
                     * A count variable is used since if the error port is detected then multicast only needs to happen 4 times for sending the agreed priority.
                     */
                        counter--;
                        int count = 0;
                        if (error_port.equals("0") != true) {
                            count = 1;
                            checkpoint = true;
                        }
                        Log.e(TAG, "The sequence number is received.");
                        Log.e(TAG, "Counter: " + counter);

                   /*
                    * Agreed priority = max of all proposed priorities.
                    */
                        agreed_sequence_number = Math.max(agreed_sequence_number, proposed_sequence_number);

                        //------------------------------------------
                        //The following code was trial for resolving the issue of "Different message under the same sequence" where the priorities were equal.
                        //However, all trying out multiple methods using hashmaps, lists..the implementation was still not consistent throughout all processes.
                        //Therefore, the port number was used since that worked the best with the priority queue and was very easy to implement.
                        //while (temp_sequence_number.contains(agreed_sequence_number)){
                        //    agreed_sequence_number++;
                        //}
                        //temp_sequence_number.add(agreed_sequence_number);

                        //            if(priorities.size() > 1){
                        //                for (int i = 0; i < priorities.size(); i++){
                        //                    //if (priorities.containsValue(agreed_sequence_number)){
                        //                        if (priorities.get(i).sequence_number == agreed_sequence_number){
                        //                            if(Integer.parseInt(priorities.get(i).process_id) < Integer.parseInt(message_handler.process_id)){
                        //                                agreed_sequence_number++;
                        //                            }else{
                        //                                priorities.get(i).sequence_number++;
                        //                            }
                        //                        }
                        //                    //}
                        //                }
                        //            }
                        //
                        //            priorities.put(agreed_sequence_number, message_handler);

                        //            priorities[index] = agreed_sequence_number;
                        //            if (checkpoint = true) {
                        //                for (int i = 0; i < priorities.length; i++) {
                        //                    if (priorities[i] == agreed_sequence_number) {
                        //                        agreed_sequence_number++;
                        //                    }
                        //                }
                        //            }

                        //if (agreed_sequence_number == proposed_sequence_number){
                        //    Log.e(TAG, "The sequence numbers are the same");
                        //    MessageHandler messageHandler = message_handler;
                        //    message_queue.remove(messageHandler);
                        //    agreed_sequence_number = proposed_sequence_number;
                        //    message_handler.sequence_number = agreed_sequence_number;
                        //    message_queue.add(message_handler);
                        //}
                        //------------------------------------------

                    /*
                     * After the agreed priority is compared among all process, the agreed priority will be chosen.
                     * The message can then be multicasted using the BufferedWriter as done previously.
                     */
                        if (counter == count) {
                        /*
                         * The message status is now deliverable.
                         */
                            message_status = "deliverable";
                        /*
                         * The format of the message is similar to initial multicast.
                         * The new message comprises of parameters seperated by semicolon (;) notation.
                         */
                            String message_agreed = message + ";" + message_status + ";" + Integer.toString(agreed_sequence_number) + ";" + port + ";" + new_message_id;

                        /*
                         * For this multicast, a new set of ports were created and closed simultaneously.
                         */
                            for (int k = 0; k < ports.length; k++) {

                                String new_port = ports[k];
                                /*
                                 * Similar to before, a check for error port before socket creation.
                                 * A new set of sockets are created for multicasting and immediately closed afterwards.
                                 */
                                if (new_port.equals(error_port) != true) {
//
                                    Socket new_socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(new_port));

                                    Log.e(TAG, "Agreed sequence number" + String.valueOf(agreed_sequence_number));
                                /*
                                 * Inverse to the InputStreamReader which reads the message, OutputStreamWriter can be used to write the message.
                                 * OutputStream is superclass representing output stream of bytes (https://developer.android.com/reference/java/io/OutputStream.html).
                                 * OutputStreamWriter bridge from character streams to bytes (https://developer.android.com/reference/java/io/OutputStreamWriter.html).
                                 * It is very important to make sure that when writing using the bufferedwriter, the messages are ended with a new line notation.
                                 */
                                    BufferedWriter new_output = new BufferedWriter(new OutputStreamWriter(new_socket.getOutputStream()));
                                    new_socket.setSoTimeout(1000);
                                    new_output.write(message_agreed + "\n");
                                    new_output.flush();
                                    new_socket.close();

                                }
                            }
                            //counter = counter - 1;
                        }


                        socket.close();
                    }

                } catch (SocketTimeoutException e){
                    error_port = temp;
                    manageQueue();
                } catch (StreamCorruptedException e){
                    error_port = temp;
                    manageQueue();
                } catch (EOFException e){
                    error_port = temp;
                    manageQueue();
                } catch (IOException e){
                    error_port = temp;
                   //doInBackground(strings);
                    manageQueue();
                } catch (Exception e) {
                    Log.e(TAG,"Exception reached!");
                    //failed_avd.add(Integer.toString(failed_port));
                    error_port = temp;
                    /*
                     * The goal is to remove the pending messages from the queue that are marked undeliverable before.
                     */
                    manageQueue();
//                    if(error_port.equals("11124")){
//                        doInBackground(strings);
//                    }
                }
            }

            return null;
        }
    }
    /*
     * To remove all the undeliverable messages from the failed port in the message queue
     */
    private void manageQueue(){
        /*
         * The documentation of Priority Queue suggests that an iterator can be used to parsing (https://docs.oracle.com/javase/7/docs/api/java/util/PriorityQueue.html)
         * The iterator has two methods; hasNext() that checks for the next element in queue and next() that retrieves the element (https://developer.android.com/reference/java/util/Iterator.html)
         * If the queue contains the message from the error port whose agreed priority is not yet chosen, then the element is removed from the queue.
         */
        //------------------------------------------
        //This was a rather inefficient way of updating the queue the Priority Queue documentation defines that an Iterator can be used to parse the queue.
//                    PriorityQueue<MessageHandler> temp_queue = message_queue;
//                    List<MessageHandler> temp_list = new ArrayList<MessageHandler>();
//                    List<MessageHandler> comparison_list = new ArrayList<MessageHandler>();
//                    comparison_list.clear();
//                    while (temp_queue.size() != 0) {
//                        comparison_list.add(temp_queue.peek());
//                        temp_queue.poll();
//                    }
//                    for (int l = 0; l < comparison_list.size(); l++) {
//                        if (comparison_list.get(l).message_status.equals("undeliverable") && comparison_list.get(l).port == Integer.parseInt(error_port)) {
//                            //do nothing
//                            //} else{
//                            //temp_queue.add(comparison_list.get(l));
//                            temp_list.add(comparison_list.get(l));
//                        }
//                    }
//                    //message_queue = temp_queue;
//                    message_queue.removeAll(temp_list);
        //------------------------------------------
        synchronized (message_queue) {
            Iterator<MessageHandler> iterator = message_queue.iterator();
            while (iterator.hasNext()==true) {
                MessageHandler local = iterator.next();
                if (local.port == Integer.parseInt(error_port) && local.message_status.equals("undeliverable")) {
                    message_queue.remove(local);
                }
            }
            Log.e(TAG, "The exception is dealt with");
        }
    }
    /*
     * A personalized comparable is used for the priority queue. (https://developer.android.com/reference/java/lang/Comparable.html)
     */
    private class MessageHandler implements Comparable<MessageHandler>{

        /*
         * Each message will have an integer id, a string status of "deliverable" or "undeliverable" and a sequence number and port.
         * The message string will be at 0th index of message array.
         * The message status will be at 1st index of message array.
         * The message sequence number will be at 2nd index of message array.
         * The port will be the 3rd index of message array.
         * The message id will be at 4th index of message array.
         */
        String message;
        int message_id;
        String message_status;
        int sequence_number;

        /*
         * This was added to handle the scenario when the sequence numbers are identical.
         */
        int port;

        @Override
        public int compareTo(MessageHandler stored_message) {
            if(this.sequence_number < stored_message.sequence_number){
                return -1;
            }

            if(this.sequence_number > stored_message.sequence_number) {
                return 1;
            }

            if(this.sequence_number == stored_message.sequence_number){
                if(this.port< stored_message.port){
                    return -1;
                }
                else{
                    return 1;
                }
            }

            return 0;
        }

        /*
         * This is needed for comparing the messagehandlers before they are delivered. (https://docs.oracle.com/javase/7/docs/api/java/lang/Object.html#equals(java.lang.Object), https://www.leepoint.net/data/expressions/22compareobjects.html)
         */
        public boolean equals(Object object){
            MessageHandler messageHandler = (MessageHandler) object;
            return this.message.equals(messageHandler.message)&&this.message_id==messageHandler.message_id;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

}