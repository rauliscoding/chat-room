import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;



public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    static private ByteBuffer writeBuffer = ByteBuffer.allocate(16384); //write buffer to server
    static private ByteBuffer bufferedReader = ByteBuffer.allocate(16384); //read buffer from server
    static private SocketChannel sc;
    static private Charset charset = Charset.forName("UTF8");
    static private CharsetDecoder decoder = charset.newDecoder();
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
		    try {
			newMessage(chatBox.getText());
		    } catch (IOException ex) {
		    } finally {
			chatBox.setText("");
		    }
		}
	    });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
	sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(new InetSocketAddress(server, port));
        
	while(!sc.finishConnect()){};

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
	if(sc.isConnected()){
	    writeBuffer.clear();
	    writeBuffer.put(message.getBytes());  //escrever para o buffer
	    writeBuffer.flip();                   //buffer passa a modo de escrita (deixa-se ler)
	    sc.write(writeBuffer);                //enviar para a socketchannel
	}
    }

    
    // Método principal do objecto
    public void run() throws IOException {
	
        // PREENCHER AQUI
	String fromServer;
	while(sc.isConnected()) {
	    
	    bufferedReader.clear();
	    sc.read(bufferedReader);
	    bufferedReader.flip();
	    fromServer = decoder.decode(bufferedReader).toString();
	    printMessage(fromServer);
	    
	}

    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
