import org.smartboot.socket.MessageProcessor;
import org.smartboot.socket.StateMachineEnum;
import org.smartboot.socket.transport.AioSession;

import static org.smartboot.socket.StateMachineEnum.SESSION_CLOSED;

/**
 * @author BOBIN YUAN s3677943@student.rmit.edu.au
 * <p>
 * Handle the message the client receive and the state event
 */
public class ClientMsgProcessor implements MessageProcessor<String> {

    @Override
    public void process(AioSession<String> session, String msg) {
        System.out.println(msg);
        //Change the client state according to the server reply
        if (msg.startsWith("Game round start")) {
            if (Client.AUTO_GUESS){
                System.out.println("Start auto guess process");
                Client.autoGuess();
            }
            Client.GAMING = true;
            Client.PENDING = false;
            Client.UNDEFINE = false;
        }
        if (msg.contains("pending queue")) {
            Client.GAMING = false;
            Client.PENDING = true;
            Client.UNDEFINE = false;
        }
        if (msg.startsWith("---> Type p")) {
            Client.GAMING = false;
            Client.PENDING = false;
            Client.UNDEFINE = true;
        }
    }

    @Override
    public void stateEvent(AioSession<String> session,
                           StateMachineEnum stateMachineEnum,
                           Throwable throwable) {
        if (stateMachineEnum == SESSION_CLOSED) {
            System.out.println("Remote connection closed, quit game now");
            System.exit(0);
        }
    }
}