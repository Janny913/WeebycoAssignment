import java.net.Socket;

/**
 * Created by jianiyang on 16/5/29.
 */

public class requestHandler implements Runnable{
    Socket socket;

    public requestHandler(Socket socket){this.socket = socket;}

    public void run(){
        handleClient(socket);
    }
}
