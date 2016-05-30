import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public abstract class ChatServer {
    public int port;

    public ChatServer(){}

    protected abstract void bind(int port); //Bind socket to port
    protected abstract void createThread() throws IOException;
    protected abstract void userCommand(String username, InputStream in, OutputStream out, Socket sock) throws IOException;
    protected abstract String getUserName(InputStream in, OutputStream out, Socket sock) throws IOException;
    protected abstract void handleClient(Socket sock);

}