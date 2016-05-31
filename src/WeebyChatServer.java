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
    private static final String SERVER_ARROW = ">> ";
    //private static final String CLIENT_ARROW = "=> ";

    //Since it's a little tricky to design the Arrow, Here I used ">>" in general.

    private Lock lockSocks; // for the list of sockets
    private Lock lockChatrooms; // for the list of people in each chatroom
    private HashMap<String,Socket> socks; // list of sockets for the chatroom
    private HashMap<String, HashSet<String>> chatrooms; // chatroom and chatroom members
    private ServerSocket server_sock;
    private boolean nameChangeFailed;

    public WeebyChatServer(int port){
        lockSocks = new ReentrantLock();
        lockChatrooms = new ReentrantLock();
        socks = new HashMap<String, Socket>();
        chatrooms = new HashMap<String, HashSet<String>>();
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
                    handleRequests rh = new handleRequests(sock);
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

        String welcome = SERVER_ARROW + "This is Jiani Yang's Chat Server for Weeby! \n";
        welcome += SERVER_ARROW + "Please create a User name. \n";
        welcome += SERVER_ARROW;

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
                String tryAgain = SERVER_ARROW + "User Name has been taken. Please pick a new one. \n";
                tryAgain += SERVER_ARROW + "New User Name: ";

                try{
                    out.write(tryAgain.getBytes());
                }
                catch (IOException exception){
                    System.err.println("Error prompting user pick a user name");
                }
            }
            lockSocks.unlock();

            if(!loop){
                String newUserWelcome = SERVER_ARROW + "Welcome" + userName + "!\n";
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

        String commandList = SERVER_ARROW + "The commands has been listed below.\n";
        commandList += SERVER_ARROW + "* /createRoom <Name>: create a chat room.\n";
        commandList += SERVER_ARROW + "* /deleteRoom <Name>: delete a chat room.\n";
        commandList += SERVER_ARROW + "* /rooms: List all rooms and peope number.\n";
        commandList += SERVER_ARROW + "* /join <Name>: Join the particular room.\n";
        commandList += SERVER_ARROW + "* /changeUserName <Name>: Change your user name.\n";
        commandList += SERVER_ARROW + "* /users: List all the online users.\n";
        commandList += SERVER_ARROW + "* /help: List command options.\n";
        commandList += SERVER_ARROW + "* /quit: Exit from the chat server.\n";

        try{
            out.write(commandList.getBytes());
            out.write(SERVER_ARROW.getBytes());
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
            else if(command[0].equals("/changeUserName")){
                print = changeUserName(command, username);
                if(!nameChangeFailed){
                    username = print;
                    print = SERVER_ARROW + "Name has been changed to: " + print + "\n";
                    nameChangeFailed = false;
                }
            }
            else if (command[0].equals("/users")){
                print = printUsers(username);
            }
            else if (command[0].equals("/help")){
                print = commandList;
            }
            else if (command[0].equals("/quit")){
                quit(username, out);
            }
            else {
                print = SERVER_ARROW + "Invalid command. Input \'/help\' for the command list.\n";
            }

            try {
                out.write(print.getBytes());
                out.write(SERVER_ARROW.getBytes());
            }
            catch (IOException exception){
                System.err.println("Issue printing reply to " + username + "'s command request");
            }
            if(len == -1){
                removeFromSocks(username);
                return;
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

    public void handleClient(Socket sock){
        InputStream in = null;
        OutputStream out = null;
        try {
            in = sock.getInputStream();
            out = sock.getOutputStream();
        }
        catch (IOException exception){
            System.err.println("Error: message send failed!");
            return;
        }

        try {
            String username = getUserName(in, out, sock);
            userCommand(username, in, out, sock);
        }
        catch (IOException exception){
            System.err.println("Error getting new user name!");
        }
    }

    private String createRoom(String[] command){
        if(command.length < 2){
            String incorrectArgs = SERVER_ARROW + "Please indicate your room name to create.\n";
            return incorrectArgs;
        }

        String roomName = getRestofCommand(command);
        lockChatrooms.lock();
        chatrooms.put(roomName, new HashSet<String>());
        lockChatrooms.unlock();

        String created = SERVER_ARROW + roomName + " created successfully!\n";
        return created;
    }

    private String deleteRoom(String[] command){
        if(command.length < 2){
            String incorrectArgs = SERVER_ARROW + "Please indicate your room name to delete.\n";
            return incorrectArgs;
        }
        String roomName = getRestofCommand(command);
        lockChatrooms.lock();
        if(!chatrooms.containsKey(roomName)){
            String noRoom = SERVER_ARROW + "There's no room called " + roomName + "\n";
            lockChatrooms.unlock();
            return noRoom;
        }

        HashSet<String> members = chatrooms.get(roomName);
        if(members.size() != 0) {
            String noDelete = SERVER_ARROW + "There's someone in it. You are not able to delete it!\n";
            lockChatrooms.unlock();
            return noDelete;
        }

        chatrooms.remove(roomName);
        lockChatrooms.unlock();

        String deleted = SERVER_ARROW + roomName + " deleted. \n";
        return deleted;
    }

    private String printRooms(){
        lockChatrooms.lock();
        if(chatrooms.size() == 0){
            String noRooms = SERVER_ARROW + "There's no room right now.\n";
            lockChatrooms.unlock();
            return noRooms;
        }
        String rooms = SERVER_ARROW + "Rooms as following: \n";
        for(String chatRoomName : chatrooms.keySet()){
            HashSet<String> members = chatrooms.get(chatRoomName);
            rooms += SERVER_ARROW + chatRoomName + " (" + members.size() + ") \n";
        }
        lockChatrooms.unlock();
        return rooms;
    }

    private String join(String[] command, String username, Socket sock){
        if(command.length < 2){
            String incorrectArgs = SERVER_ARROW + "Please indicate room name.\n";
            return incorrectArgs;
        }
        String roomName = getRestofCommand(command);
        if(!chatrooms.containsKey(roomName)){
            String noRoom = SERVER_ARROW + "That's not an available room.\n";
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
        String welcome = SERVER_ARROW + "Welcome to " + roomName + "!\n" + SERVER_ARROW;
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
            users.append(SERVER_ARROW).append(" ").append(n).append(" ");
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

    private int chat(String roomName, String username, InputStream in, OutputStream out, Socket sock) throws IOException{
        String help = "You can use the following commands in your chatting room: \n";
        help += SERVER_ARROW + "* /leave: to leave the chatroom \n";
        help += SERVER_ARROW + "* /users: prints out the list of users are online \n";
        help += SERVER_ARROW + "* /help <Room Name>: lists these command options \n";
        help += SERVER_ARROW + "* /quit: to exit the chat server \n";

        try{
            out.write(help.getBytes());
            out.write(SERVER_ARROW.getBytes());
        }
        catch (IOException exception){
            System.err.println("Printing commands error!");
        }

        byte[] data = new byte[2000];
        int len = 0;

        while((len = in.read(data)) != -1){
            String message = new String(data, 0, len);

            String[] command = message.substring(0, message.length()-2).split(" ");
            String print = "";
            boolean needArrow = true;
            if(command[0].equals("/leave")){
                String leftRoom = "User " + username + " has left the room.";
                sendMessageToChatroom(roomName, leftRoom, username);
                lockChatrooms.lock();
                HashSet<String> members = chatrooms.get(roomName);
                members.remove(username);
                lockChatrooms.unlock();
                return 0;
            }
            else if(command[0].equals("/users")){
                print = printUsers(username);
            }
            else if (command[0].equals("/quit")){
                String leftRoom = "User" + username + "has left the room.";
                sendMessageToChatroom(roomName, leftRoom, username);
                lockChatrooms.lock();
                HashSet<String> members = chatrooms.get(roomName);
                members.remove(username);
                lockChatrooms.unlock();
                quit(username, out);
                return -1;
            }
            else if (command[0].equals("/help")){
                print = SERVER_ARROW + help;
            }
            else {
                out.write(SERVER_ARROW.getBytes());
                sendMessage(roomName,username + ": " + message, username);
                needArrow = false;
            }

            try {
                out.write(print.getBytes());
                if(needArrow) out.write((SERVER_ARROW.getBytes()));
            }
            catch (IOException exception){
                System.err.println("Printing the arrow for user failed");
            }
        }
        if(len == -1){
            String leftRoom = "User" + username + "has left the room.";
            sendMessageToChatroom(roomName, leftRoom, username);
            lockChatrooms.lock();
            HashSet<String> members = chatrooms.get(roomName);
            members.remove(username);
            lockChatrooms.unlock();

            removeFromSocks(username);
            return -1;
        }
        return 0;
    }

    private void sendMessage(String roomName, String message, String username){
        byte[] m = (message + SERVER_ARROW).getBytes();
        OutputStream out;
        Socket s;

        lockChatrooms.lock();
        lockSocks.lock();
        HashSet<String> members = chatrooms.get(roomName);
        Iterator<String> it = members.iterator();

        while(it.hasNext()) {
            String n = it.next();
            try {
                s = socks.get(n);
                out = s.getOutputStream();
                out.write(m);
            }
            catch (IOException exception){
                System.err.println("Message sending failed for " + n);
                lockSocks.unlock();
                lockChatrooms.unlock();
                return;
            }
        }

        lockSocks.unlock();
        lockChatrooms.unlock();
    }

    private void sendMessageToChatroom(String roomName, String message, String username){
        byte[] mToRest = (message + "\n" + SERVER_ARROW).getBytes();
        byte[] mToSender = (SERVER_ARROW + message + " " + YOURSELF + "\n").getBytes();
        byte[] m = mToRest;
        OutputStream out;
        Socket s;

        lockChatrooms.lock();
        lockSocks.lock();
        HashSet<String> members = chatrooms.get(roomName);
        Iterator<String> it = members.iterator();
        while (it.hasNext()) {
            String n = it.next();
            try {
                s = socks.get(n);
                out = s.getOutputStream();
                if (n.equals(username)) {
                    m = mToSender;
                }
                out.write(m);
                m = mToRest;
            } catch (IOException e) {
                System.err.println("Error: message sending failed for: " + n);
                lockChatrooms.unlock();
                lockSocks.unlock();
                return;
            }
        }
        lockChatrooms.unlock();
        lockSocks.unlock();
    }

    private String changeUserName(String[] command, String username){
        if(command.length < 2){
            String incorrectArgs = SERVER_ARROW + "Please indicate a username you want to change.\n";
            nameChangeFailed = true;
            return incorrectArgs;
        }

        String desireName = getRestofCommand(command);
        if (socks.containsKey(desireName)){
            String nameTaken = SERVER_ARROW + "This name has already been taken.\n";
            nameChangeFailed = true;
            return nameTaken;
        }

        nameChangeFailed = false;
        changeUserName(username, desireName);
        return  desireName;
    }

    private void changeUserName(String curName, String desireName){
        lockSocks.lock();
        Socket sock = socks.get(curName);
        socks.remove(curName);
        socks.put(desireName, sock);
        lockSocks.unlock();
    }

    private String printUsers(String username){
        StringBuilder users = new StringBuilder(SERVER_ARROW + "The following users are online: \n");

        lockSocks.lock();
        for (String user : socks.keySet()) {
            users.append(SERVER_ARROW).append(" ").append(user);
            if(user.equals(username)) {
                users.append(" ").append(YOURSELF);
            }
            users.append("\n");
        }
        lockSocks.unlock();
        return users.toString();
    }

    protected void quit(String username, OutputStream out){
        String bye = SERVER_ARROW + "Bye!\n";
        try {
            out.write(bye.getBytes());
        }
        catch (IOException exception){
            System.err.println("Problem to say goodbye from chat server!");
        }
        removeFromSocks(username);
    }

    private void removeFromSocks(String username){
        lockSocks.lock();
        Socket sock = socks.get(username);
        socks.remove(username);
        lockSocks.unlock();
        try {
            sock.close();
        }
        catch (IOException exception){
            System.err.println("Error when closing " + username + " socket!");
        }
        return;
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

    public class handleRequests implements Runnable{
        Socket socket;

        public handleRequests(Socket socket){
            this.socket = socket;
        }

        public void run(){
            handleClient(socket);
        }
    }
}
