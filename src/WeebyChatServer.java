/**
 * Created by jianiyang on 16/5/29.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;
//import java.util.concurrent.locks.ReentrantLock;

public class WeebyChatServer extends ChatServer {
    private static final int DEFAULT_PORT = 8080;
    private static final String YOURSELF = "(Yourself)";
    private static final String ARROW = ">> ";

    private Lock lockSocks; // for the list of sockets
    private Lock lockChatrooms; // for the list of people in each chatroom
    private Lock lockReplies; // for the list of users to reply to
    private HashMap<String,Socket> socks; // list of sockets for the chatroom
    private HashMap<String, HashSet<String>> chatrooms; // chatroom and chatroom members
    private HashMap<String, String> replyTo; // keep tracking of who to reply to for users
    private ServerSocket server_sock;
    private boolean nameChangeFailed;

    public WeebyChatServer(int port){
        lockSocks = new ReentrantLock();
        lockChatrooms = new ReentrantLock();
        lockReplies = new ReentrantLock();
        socks = new HashMap<String, Socket>();
        chatrooms = new HashMap<String, HashSet<String>>();
        replyTo = new HashMap<String, String>();
        nameChangeFailed = false;

        chatrooms.put("Main Room", new HashSet<String>());

        bind(port);
        try{
            createThread();
        }
        catch (IOException exception){
            System.err.println("Error when create thread for the new client");
        }
    }

    protected void bind(int port){
        try{
            server_sock = new ServerSocket(port);
            server_sock.setReuseAddress(true);
        }
        catch (IOException exception){
            System.err.println("Failed to create socket!");
            System.exit(1);
        }
        catch (IllegalArgumentException exception){
            System.err.println("Error binding to the port!");
            System.exit(1);
        }
    }

    protected void createThread() throws IOException{
        try{
            while(true){
                try{
                    Socket sock = server_sock.accept();
                    requestHandler rh = new requestHandler(sock);
                    Thread t = new Thread(rh);
                    t.start();
                }
                catch (IOException exception){
                    System.err.println("Error accepting Connection!");
                    continue;
                }
            }
        }
        finally {
            server_sock.close();
        }
    }

    protected String getUserName(InputStream in, OutputStream out, Socket sock) throws IOException{

    }

    protected void userCommand(String username, InputStream in, OutputStream out, Socket sock) throws IOException{

    }

    protected void handleClient(Socket sock){

    }

    public static void main(String[] arg){
        if (arg.length > 1){
            System.err.println("You just need to input the port #.");
            System.exit(-1);
        }

        int port = DEFAULT_PORT;
        if(arg.length == 1){
            try{
                port = Integer.parseInt(arg[0]);
            }
            catch (NumberFormatException exception){
                System.err.println("Invalid port #!");
                System.exit(-1);
            }
        }

        ChatServer myServer = new WeebyChatServer(port);
    }
}
