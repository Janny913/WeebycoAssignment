public abstract class ChatServer {
    public static final int DEFAULT_PORT = 8080;
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
}