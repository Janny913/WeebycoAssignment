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
        byte[] data = new byte[2000];
        int len = 0;

        String welcome = ARROW + "This is Jiani Yang's Chat Server for Weeby! \n";
        welcome += ARROW + "Please create a User name. \n";
        welcome += ARROW;

        try{
            out.write(welcome.getBytes());
        }
        catch (IOException exception){
            System.err.println("Error printing out the Welcome message!");
        }

        String userName = "";
        boolean loop = false;

        while ((len = in.read(data)) != -1){
            userName = new String(data, 0, len-2);
            lockSocks.lock();
            if(!socks.containsKey(userName)){
                loop = false;
                socks.put(userName, sock);
            }
            else {
                loop = true;
                String tryAgain = ARROW + "User Name has been taken. Please pick a new one. \n";
                tryAgain += ARROW + "New User Name: ";

                try{
                    out.write(tryAgain.getBytes());
                }
                catch (IOException exception){
                    System.err.println("Error prompting user pick a user name");
                }
            }
            lockSocks.unlock();

            if(!loop){
                String newUserWelcome = ARROW + "Welcome" + userName + "!\n";
                try{
                    out.write(newUserWelcome.getBytes());
                }
                catch (IOException exception){
                    System.err.println("Error Welcoming");
                }
                break;
            }
        }
        if(len == -1){
            sock.close();
            return "";
        }
        return userName;
    }

    protected void userCommand(String username, InputStream in, OutputStream out, Socket sock) throws IOException{
        byte[] data = new byte[2000];
        int len = 0;

        String commandList = ARROW + "The commands has been listed below.\n";
        commandList += ARROW + "* /createRoom <Name>: create a chat room.\n";
        commandList += ARROW + "* /deleteRoom <Name>: delete a chat room.\n";
        commandList += ARROW + "* /rooms: List all rooms and peope number.\n";
        commandList += ARROW + "* /join <Name>: Join the particular room.\n";
        commandList += ARROW + "* /changeUserName <Name>: Change your user name.\n";
        commandList += ARROW + "* /users: List all the online users.\n";
        commandList += ARROW + "* /help: List command options.\n";
        commandList += ARROW + "* /quit: Exit from the chat server.\n";

        try{
            out.write(commandList.getBytes());
            out.write(ARROW.getBytes());
        }
        catch (IOException exception){
            System.err.println("Print out commands failed!");
        }

        while((len = in.read(data)) != -1){
            String message = new String(data, 0, len);
            String[] command = message.substring(0, message.length()-2).split(" ");
            String print = "";
            if(command[0].equals("/createRoom")){
                print = createRoom(command);
            }
            else if(command[0].equals("/deleteRoom")){
                print = deleteRoom(command);
            }
            else if(command[0].equals("/rooms")){
                print = printRooms();
            }
            else if(command[0].equals("/join")){
                print = join(command, username, sock);
                if(print.equals("")) return;
                else if(print.equals(" ")) print = "";
            }
        }
    }

    private String getRestofCommand(String[] command){
        StringBuilder sb = new StringBuilder();
        for(int i = 1; i < command.length; i++){
            sb.append(command[i]);
            if(i != command.length - 1){
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    protected void handleClient(Socket sock){

    }

    private String createRoom(String[] command){
        if(command.length < 2){
            String incorrectArgs = ARROW + "Please indicate your room name to create.\n";
            return incorrectArgs;
        }

        String roomName = getRestofCommand(command);
        lockChatrooms.lock();
        chatrooms.put(roomName, new HashSet<String>());
        lockChatrooms.unlock();

        String created = ARROW + roomName + " created successfully!\n";
        return created;
    }

    private String deleteRoom(String[] command){
        if(command.length < 2){
            String incorrectArgs = ARROW + "Please indicate your room name to delete.\n";
            return incorrectArgs;
        }
        String roomName = getRestofCommand(command);
        lockChatrooms.lock();
        if(!chatrooms.containsKey(roomName)){
            String noRoom = ARROW + "There's no room called " + roomName + "\n";
            lockChatrooms.unlock();
            return noRoom;
        }

        HashSet<String> members = chatrooms.get(roomName);
        if(members.size() != 0) {
            String noDelete = ARROW + "There's someone in it. You are not able to delete it!\n";
            lockChatrooms.unlock();
            return noDelete;
        }

        chatrooms.remove(roomName);
        lockChatrooms.unlock();
    }

    private String printRooms(){
        lockChatrooms.lock();
        if(chatrooms.size() == 0){
            String noRooms = ARROW + "There's no room right now.\n";
            lockChatrooms.unlock();
            return noRooms;
        }
        String rooms = ARROW + "Rooms as following: \n";
        for(String chatRoomName : chatrooms.keySet()){
            HashSet<String> members = chatrooms.get(chatRoomName);
            rooms += ARROW + chatRoomName + " (" + members.size() + ") \n";
        }
        lockChatrooms.unlock();
        return rooms;
    }

    private String join(String[] command, String username, Socket sock){
        if(command.length < 2){
            String incorrectArgs = ARROW + "Please indicate room name.\n";
            return incorrectArgs;
        }
        String roomName = getRestofCommand(command);
        if(!chatrooms.containsKey(roomName)){
            String noRoom = ARROW + "That's not an available room.\n";
            return noRoom;
        }
        try {
            newUserToRoom(username, roomName, sock);
            if(chat(roomName, username, sock.getInputStream(), sock.getOutputStream(), sock) == -1){
                return "";
            }
        }
        catch (IOException exception){
            System.err.println("Error getting " + username + "'s in/out stream");
        }
        return " ";
    }

    private void newUserToRoom(String username, String roomName, Socket newSock){
        String welcome = ARROW + "Welcome to " + roomName + "!\n" + ARROW;
        try {
            newSock.getOutputStream().write(welcome.getBytes());
        }
        catch (IOException exception){
            System.err.println("Error welcoming " + username);
        }

        lockChatrooms.lock();
        HashSet<String> members = chatrooms.get(roomName);
        members.add(username);
        lockChatrooms.unlock();

        //tell room members, there's new member entered.
        String message = "Entering room: " + username + "\n";
        sendMessage(roomName, message, username);
        StringBuilder users = new StringBuilder();
        users.append("Current users: \n");
        Socket s;
        OutputStream out;
        lockChatrooms.lock();
        lockSocks.lock();
        Iterator<String> it = members.iterator();
        while (it.hasNext()){
            String n = it.next();
            s = socks.get(n);
            users.append(ARROW).append(" ").append(n).append(" ");
            if(s == newSock){
                users.append(YOURSELF);
            }
            users.append("\n");
        }
        lockSocks.unlock();
        lockChatrooms.unlock();

        try{
            newSock.getOutputStream().write(users.toString().getBytes());
        }
        catch (IOException exception){
            System.err.println("Error: message sending failed for: " + username);
        }
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
