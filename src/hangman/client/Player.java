/**
 * @author Marco Ceccotti
*/

package hangman.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Scanner;

import org.jasypt.util.text.BasicTextEncryptor;
import org.json.simple.JSONObject;

import hangman.utils.Message;

public abstract class Player extends Thread
{
	/* own username */
	protected String username;
	/* username of the match's master */
	protected String master;
	/* number of users nedded to start a match */
	protected int customers;
	/* socket input interface */
	protected ObjectInputStream in;
	/* socket output interface */
	protected PrintWriter out;
	/* the TCP socket */
	protected Socket socket;
	/* state of the match */
	protected int state;
	/* determines if the match is closed by external events */
	protected boolean closed_external = true;
	/* determines if the user is playing */
	protected boolean in_game = false;
	/* the UDP multicast socket */
	protected MulticastSocket m_socket;
	/* multicast address */
	protected InetAddress m_address;
	/* last received packet */
	protected DatagramPacket packet;
	/* server UDP port */
	protected int port;
	/* cryptographic key */
	protected String key;

	/* the encryptor */
	protected static final BasicTextEncryptor ENCRYPTOR = new BasicTextEncryptor();
	/* state of the match */
	protected static final int WAIT_MATCH = 0, START_MATCH = 1;
	/* timer duration of the match (5 minutes) */
	protected static final int TIME_MATCH = 300000;
	/* maximum number of bytes for a UDP message */
	protected static final int MAX_BUFFER_SIZE = 65536;

	/** create a new instance of the player
	 * 
	 * @param customers			number of customers requested (significant only for the master)
	 * @param socket			the TCP socket
	 * @param master_username	master username
	 * @param username			own username
	*/
	public Player( final int customers, final Socket socket, final String master_username, final String username ) throws IOException
	{
		this.customers = customers;
		this.username = username;
		master = master_username;

		this.socket = socket;
		in = new ObjectInputStream( socket.getInputStream() );
		out = new PrintWriter( socket.getOutputStream(), true );

		// send the hello message containing the own username (the Worker doesn't know it)
		out.println( Message.HELLO + username );

		state = WAIT_MATCH;
	}

	/** check if the player is waiting or playing a match
	 * 
	 * @return TRUE if it is, FALSE otherwise
	*/
	public boolean isInGame()
	{
		return in_game;
	}

	/** close the TCP connection */
	protected void closeTCPConnection()
	{
		try{ in.close(); }
		catch( IOException e ){}
		out.close();
		try{ socket.close(); }
		catch( IOException e ){}
	}

	/** join a multicast group
	 * 
	 * @param port			the selected port
	 * @param m_address		the multicast address
	*/
	protected MulticastSocket join( final int port, final InetAddress m_address ) throws UnknownHostException, IOException, SocketException
	{
		MulticastSocket socket = new MulticastSocket( port );
		socket.setLoopbackMode( true );

		// interface used for the multicast
		InetAddress iface = InetAddress.getByName( InetAddress.getLocalHost().getHostAddress() );
		socket.setInterface( iface );
		socket.joinGroup( m_address );

		return socket;
	}

	/** decrypt the message in the specified field
	 * 
	 * @param message	message object
	 * @param field		field to decrypt
	*/
	protected String decryptMessage( final JSONObject message, final String field )
	{
		String value = (String) message.get( field );
		return ENCRYPTOR.decrypt( value );
	}

	/** receive a new UDP message
	 * 
	 * @param amount_of_time	0 if the receive must be blocking, > 0 to wait for the specified amount ot time
	 * 
	 * @return the message, if the time is not over, null otherwise
	*/
	protected JSONObject receive( final int amount_of_time ) throws IOException, ClassNotFoundException
	{
		byte buffer[] = new byte[MAX_BUFFER_SIZE];
		packet = new DatagramPacket( buffer, MAX_BUFFER_SIZE );
		m_socket.setSoTimeout( amount_of_time );
		try{ m_socket.receive( packet ); }
		catch( SocketTimeoutException e ){ return null; }
		//buffer = ENCRYPTOR.decrypt( new String( packet.getData() ) ).getBytes();
		//JSONObject message = new JSONObject( buffer );
		//return message;

		ByteArrayInputStream baos = new ByteArrayInputStream( buffer );
		ObjectInputStream oos = new ObjectInputStream( baos );

		return (JSONObject) oos.readObject();
	}

	/** send a new message over the UDP socket
	 * 
	 * @param message	message to send
	*/
	protected void send( final JSONObject message ) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream( baos );
		oos.writeObject( message );
		oos.flush();

		//byte buffer[] = ENCRYPTOR.encrypt( message.toString() ).getBytes();
		byte buffer[] = baos.toByteArray();
		packet = new DatagramPacket( buffer, buffer.length, m_address, port );
		m_socket.send( packet );
	}

	/** send a message only to the specified user
	 * 
	 * @param address	receiver IP address
	 * @param message	message to send
	*/
	protected void sendToGuesser( final String address, final JSONObject message ) throws IOException
	{        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream( baos );
		oos.writeObject( message );
		oos.flush();

		byte buffer[] = baos.toByteArray();
		packet = new DatagramPacket( buffer, buffer.length, InetAddress.getByName( address ), port );
		DatagramSocket socket = new DatagramSocket();
        socket.send( packet );
        socket.close();
	}

	/** close the UDP connection */
	protected void closeUDPConnection()
	{
		try{ m_socket.leaveGroup( m_address ); }
		catch( IOException e ){}
		m_socket.close();
	}

	/** the user input is processed here according to the application state
	 * 
	 * @param scan		input scanner
	 * @param input		the input value
	*/
	public abstract void inputManagement( final Scanner scan, final String input ) throws SocketException, IOException;

	/** play section */
	protected abstract void playGame() throws IOException, ClassNotFoundException;
}