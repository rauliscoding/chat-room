import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

enum State{ init, outside, inside};

class Client{
    String nickname;
    String chatroom;
    State state;
    StringBuffer buffer;
    
    Client(){
	nickname = null;
	chatroom = null;
	state = State.init;
	buffer = new StringBuffer();
    }
	
}

public class ChatServer
{
    // A pre-allocated buffer for the received data
    static private final ByteBuffer bufferedReader = ByteBuffer.allocate( 16384 );
    static private final ByteBuffer writeBuffer = ByteBuffer.allocate( 16384 );
	

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    //hash com todos os clientes conectados
    static private final HashMap<SocketChannel, Client > clientList = new HashMap<>();

    static public void main( String args[] ) throws Exception {
	// Parse port from command line
	int port = Integer.parseInt( args[0] );
    
	try {
	    // Instead of creating a ServerSocket, create a ServerSocketChannel
	    ServerSocketChannel ssc = ServerSocketChannel.open();

	    // Set it to non-blocking, so we can use select
	    ssc.configureBlocking( false );

	    // Get the Socket connected to this channel, and bind it to the
	    // listening port
	    ServerSocket ss = ssc.socket();
	    InetSocketAddress isa = new InetSocketAddress( port );
	    ss.bind( isa );

	    // Create a new Selector for selecting
	    Selector selector = Selector.open();

	    // Register the ServerSocketChannel, so we can listen for incoming
	    // connections
	    ssc.register( selector, SelectionKey.OP_ACCEPT );
	    System.out.println( "Listening on port "+port );

	    while (true) {
		// See if we've had any activity -- either an incoming connection,
		// or incoming data on an existing connection
		int num = selector.select();

		// If we don't have any activity, loop around and wait again
		if (num == 0) {
		    continue;
		}

		// Get the keys corresponding to the activity that has been
		// detected, and process them one by one
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> it = keys.iterator();
		while (it.hasNext()) {
		    // Get a key representing one of bits of I/O activity
		    SelectionKey key = it.next();

		    // What kind of activity is it?
		    if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
			SelectionKey.OP_ACCEPT) {

			// It's an incoming connection.  Register this socket with
			// the Selector so we can listen for input on it
			Socket s = ss.accept();
			System.out.println( "Got connection from "+s );

			// Make sure to make it non-blocking, so we can use a selector
			// on it.
			SocketChannel sc = s.getChannel();
			sc.configureBlocking( false );

			// Register it with the selector, for reading
			sc.register( selector, SelectionKey.OP_READ );
			  
			clientList.put( sc , new Client());
			

		    } else if ((key.readyOps() & SelectionKey.OP_READ) ==
			       SelectionKey.OP_READ) {

			SocketChannel sc = null;

			try {

			    // It's incoming data on a connection -- process it
			    sc = (SocketChannel)key.channel();
			    boolean ok = processInput( sc );

			    // If the connection is dead, remove it from the selector
			    // and close it
			    if (!ok) {
				key.cancel();

				Socket s = null;
				try {
				    s = sc.socket();
				    System.out.println( "Closing connection to "+s );
				    s.close();
				} catch( IOException ie ) {
				    System.err.println( "Error closing socket "+s+": "+ie );
				}
			    }

			} catch( IOException ie ) {

			    // On exception, remove this channel from the selector
			    key.cancel();

			    try {
				sc.close();
			    } catch( IOException ie2 ) { System.out.println( ie2 ); }

			    System.out.println( "Closed "+sc );
			}
		    }
		}

		// We remove the selected keys, because we've dealt with them.
		keys.clear();
	    }
	} catch( IOException ie ) {
	    System.err.println( ie );
	}
    }


    // Just read the message from the socket and send it to stdout
    static private boolean processInput( SocketChannel sc ) throws IOException {
	
	// Read the message to the buffer
        bufferedReader.clear();
	sc.read( bufferedReader );
	bufferedReader.flip();

	// If no data, close the connection
	if ( bufferedReader.limit()==0 ) {
	    return false;
	}
	 
	String message = decoder.decode(bufferedReader).toString();
	
	String[] arr = message.split(" ");    
	String head = arr[0];
	
	switch(head){
	case "/nick":
	    processNick(sc, message);
	    break;
	case "/join":
	    processJoin(sc, message);
	    break;
	case "/leave":
	    processLeave(sc);
	    break;
	case "/bye":
	    processBye(sc);
	    break;
	case "/priv":
	    processPriv(sc, message);
	    break;
	default:
	    processMessage(sc, message);	  
	}
    
	
	return true;
    }
	
    public static void processNick(SocketChannel sc, String message) throws IOException{

	Client currentClient = clientList.get(sc);
	String[] arr = message.split(" ");
	
			  
	String newNick = arr[1];
	boolean used = availableNick(newNick);
			  

	//state = inside
	if(currentClient.state == State.inside){
	    if(used == true){
		sendToUser("ERROR", sc);
	    }
	    else{
		sendToUser("OK", sc);
		sendToRoom(currentClient.nickname + " mudou de nome para " + newNick, sc);
		currentClient.nickname = newNick;
	    }
				

	}  //state = outside || init
	else {
	    if(used == true){
		sendToUser("ERROR", sc);
	    }
	    else{
		currentClient.nickname = newNick;
		sendToUser("OK", sc);
		currentClient.state = State.outside;
		currentClient.nickname = newNick;
	    }
	}
    }
	
    public static void processJoin(SocketChannel sc, String message)throws IOException{
	String[] arr = message.split(" ");
	String newRoom = arr[1];
	Client currentClient = clientList.get(sc);
		
	if(currentClient.state == State.inside){

	     //estado inside e sala nao existe
	    if(sameRoom(newRoom) == false){             
		sendToRoom("LEFT " + currentClient.nickname , sc);
		sendToUser("OK", sc);
		currentClient.chatroom = newRoom;
	    }
	    else{
		//estado inside e sala existe
		sendToUser("OK", sc);
		sendToRoom("LEFT " + currentClient.nickname , sc);
		currentClient.chatroom = newRoom;
		sendToRoom("JOINED " + currentClient.nickname, sc);
	    }		
	}
	else if (currentClient.state == State.outside){

	     //estado outside e sala nao existe
	    if(sameRoom(newRoom) == false){        
		sendToUser("OK", sc);
		currentClient.chatroom = newRoom;
	    }
	    else{
		//estado outside e sala existe
		sendToUser("OK", sc);
		currentClient.chatroom = newRoom;
		sendToRoom("JOINED " + currentClient.nickname, sc);
	    }
	    currentClient.state = State.inside;
	}
	else{
	    sendToUser("ERROR", sc);
	}
		
    }
	
    public static void processLeave(SocketChannel sc)throws IOException{
	Client currentClient = clientList.get(sc);
	
	if(currentClient.state == State.inside){
	    sendToUser("OK", sc);
	    sendToRoom("LEFT " +currentClient.nickname , sc);
	    currentClient.chatroom = null;
	    currentClient.state = State.outside;
	    
	}
	else
	    sendToUser("ERROR", sc);
    }
    
    
    public static void processBye(SocketChannel sc)throws IOException{
	Client currentClient = clientList.get(sc);

	if(currentClient.state == State.inside){
	    sendToRoom("LEFT " + currentClient.nickname , sc);
	}

	sendToUser("BYE", sc);

	Socket s = null;
	try {
	    s = sc.socket();
	    System.out.println( "Closing connection to "+s );
	    s.close();
	} catch( IOException ie ) {
	    System.err.println( "Error closing socket "+s+": "+ie );
	}

	clientList.remove(sc);
    }
    
    public static void processMessage(SocketChannel sc, String message ) throws IOException {
	Client currentClient = clientList.get(sc);
	System.out.println(message);

	if(message.charAt(0) == '/')
	    message = message.substring(1);

	if(currentClient.state == State.inside){
	    sendToRoom(currentClient.nickname + ": " + message, sc);
	    sendToUser(currentClient.nickname + ": " + message, sc);
	}
	else
	    sendToUser("ERROR", sc);
    }

    public static void processPriv(SocketChannel sc, String message ) throws IOException {
	Client currentClient = clientList.get(sc);
	Scanner scanner = new Scanner(message);
	scanner.next();
	String userDestiny = scanner.next();
	String privMessage = scanner.nextLine();
	
	if(currentClient.state == State.init || availableNick(userDestiny) == false){
	    sendToUser("ERROR", sc);
	}
	else{
	    for(Map.Entry<SocketChannel, Client> clientEntry : clientList.entrySet()) {
		if(clientEntry.getValue().nickname.compareTo(userDestiny) == 0){
		    sendToUser("PRIVATE " + clientList.get(sc).nickname + ":" + privMessage, clientEntry.getKey());
		    sendToUser("OK", sc);
		    break;
		}
	    }
	}
    }
    
    public static void sendToUser(String message, SocketChannel sc) throws IOException {
	message = message + "\n";
	writeBuffer.clear();
	//escrever para o buffer
	writeBuffer.put(message.getBytes());
	//buffer permite que seja lido
	writeBuffer.flip();                  
	sc.write(writeBuffer); 
    }
	
    public static void sendToRoom(String message, SocketChannel sc) throws IOException {
	message = message + "\n";
	String currentRoom = clientList.get(sc).chatroom;
		
	for(Map.Entry<SocketChannel, Client> currentClient : clientList.entrySet()){
	    if(currentClient.getKey() == sc || currentClient.getValue().chatroom == null){}
	    else if(currentClient.getValue().chatroom.compareTo(currentRoom) == 0){
				
		writeBuffer.clear();
		//escrever para o buffer
		writeBuffer.put(message.getBytes());
		//buffer passa a modo de escrita (deixa-se ler)
		writeBuffer.flip();                  
		currentClient.getKey().write(writeBuffer);
		
	    }
				
	}
    }
	
    public static boolean availableNick(String newNickname) {
	for(Map.Entry<SocketChannel, Client> currentClient : clientList.entrySet()){
	    if(currentClient.getValue().nickname == null){}
	    else if(currentClient.getValue().nickname.compareTo(newNickname) == 0)
		return true;
	}
	return false;
    }
	
    public static boolean sameRoom(String room) {  
	if(clientList.size() > 0){
	    for(Map.Entry<SocketChannel, Client> currentClient : clientList.entrySet()){
		if(currentClient.getValue().chatroom == null){}
		else if(currentClient.getValue().chatroom.compareTo(room) == 0){
		    return true;
		}
	    }
	}

	return false;
    }
	
}
