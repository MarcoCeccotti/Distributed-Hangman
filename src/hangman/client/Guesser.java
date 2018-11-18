/**
 * @author Marco Ceccotti
*/

package hangman.client;

import hangman.utils.HelpMessage;
import hangman.utils.Message;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;

import javax.swing.Timer;

import org.json.simple.JSONObject;

public class Guesser extends Player
{
	/* message to sent */
	private JSONObject message;
	/* ack timeout */
	private Timer ackTimer;
	/* determines if the user can send another request */
	private boolean send_again = false;
	/* current packet number */
	private int packet_number = 1;

	/* timer duration for the ack packet */
	private static final int TIME_ACK = 200;
	/* regex used to check the correctness of a letter played by the user */
	private static final String CHAR_REGEX = "[a-zA-Z]";

	public Guesser( final Socket socket, final String m_username, final String username ) throws IOException
	{
		super( 0, socket, m_username, username );
	}

	@Override
	public void inputManagement( final Scanner scan, final String input )
	{
		String command = input.toLowerCase();

		if(state == WAIT_MATCH){
			if(command.equals( "exit" )){
				System.out.print( "<prompt>:: DO YOU REALLY WANT TO LEAVE YOUR GROUP? (Y or N): " );
				if(scan.nextLine().equalsIgnoreCase( "y" )){
					try{ out.println( Message.EXIT ); }
					catch( Exception e ){}

					closed_external = false;
					closeTCPConnection();

					System.out.println( "<prompt>:: YOU ARE LEAVING THE GROUP..." );
					System.out.print( "<prompt>:: " );
				}
			}
			else if(command.equals( "help" ))
				HelpMessage.print( HelpMessage.GUESSER );
			else if(!command.equals( "" ))
				System.out.println( "<prompt>:: UNKNOWN COMMAND " + input + ". TYPE help FOR COMMANDS LIST" );
		}
		else{ // START_MATCH
			if(command.length() == 1){ // it's a letter
				if(!command.matches( CHAR_REGEX ))
					System.out.println( "<prompt>:: INVALID LETTER: SELECT ONE IN THE RANGE a-z or A-Z" );
				else{
					if(send_again){
						message = createMessage( Message.NEW_LETTER, input, username );
						try{
							send( message );
						}
						catch( IOException e ){
							closed_external = false;
							closeUDPConnection();
							return;
						}
	
						ackTimer.start();
						packet_number++;
						send_again = false;
					}
					else
						System.out.println( "<prompt>:: YOU HAVE TO WAIT THE RESPONSE BEFORE THE NEXT ATTEMPT" );
				}
			}
			else if(command.equals( "exit" )){
				System.out.print( "<prompt>:: DO YOU REALLY WANT TO LEAVE THE MATCH? (Y or N): " );
				if(scan.nextLine().equalsIgnoreCase( "y" ))
					closeUDPConnection();
			}
			else if(command.equals( "help" ))
				HelpMessage.print( HelpMessage.GUESSER_GAME );
			else if(!command.equals( "" ))
				System.out.println( "<prompt>:: UNKNOWN COMMAND " + input + ". TYPE help FOR COMMANDS LIST" );
		}
	}

	@Override
	public void run()
	{
		try{
			out.println( Message.GUESSER + master );

			Timer timer_match = null;
			boolean close = false;
			JSONObject message;

			while(!close){
				// read data from the TCP socket
				message = (JSONObject) in.readObject();
				if(message == null){
					if(closed_external)
						System.out.println( "SERVER CONNECTION IS DOWN..." );

					if(timer_match != null)
						timer_match.stop();

					closeTCPConnection();
					break;
				}

				switch( (char) message.get( "type" ) ){
					case( Message.MATCH_FULL ):
						System.out.println( "THE SELECTED MATCH IS FULL" );
						closeTCPConnection();
						close = true;

						break;

					case( Message.ADDED_TO_MATCH ):
						System.out.println( "YOU HAVE BEEN ADDED TO THE MATCH" );

						in_game = true;

						timer_match = new Timer( TIME_MATCH, new ActionListener(){
							@Override
							public void actionPerformed( ActionEvent e )
							{
								out.println( Message.EXIT );
								System.out.println( "TIME IS OVER, YOUR GROUP WILL BE CLOSED" );
								closed_external = false;
								closeTCPConnection();
							}
						} );

						timer_match.start();

						System.out.print( "<prompt>:: " );

						break;

					case( Message.MASTER_DOESNT_EXIST ):
						System.out.println( "THE SELECTED MATCH DOESN'T EXIST" );
						close = true;
						closeTCPConnection();

						break;

					case( Message.MATCH_ALREADY_CLOSED ):
						System.out.println( "THE SELECTED MATCH IS ALREADY CLOSED" );
						close = true;
						closeTCPConnection();

						break;

					case( Message.MATCH_CLOSED ):
						System.out.println( "YOUR GROUP IS CLOSED" );
						close = true;
						out.println( Message.EXIT );
						closeTCPConnection();

						break;

					case( Message.START_MATCH ):
						timer_match.stop();
						state = START_MATCH;

						// used to close the server-side TCP connection
						out.println( Message.START_MATCH );

						// obtains the match settings (multicast address, port and cryptographic key)
						m_address = InetAddress.getByName( (String) message.get( "address" ) );
						port = (int) message.get( "port" );
						key = (String) message.get( "key" );

						closeTCPConnection();

						playGame();

						close = true;
						state = WAIT_MATCH;

						break;
				}
			}

			System.out.print( "<prompt>:: " );
		}
		catch( Exception e ){
			if(closed_external)
				e.printStackTrace();
		}

		in_game = false;
	}

	@Override
	protected void playGame()
	{
		ENCRYPTOR.setPassword( key );

		System.out.println( "THE MATCH IS STARTED" );
		System.out.print( "<prompt>:: " );

		send_again = true;

		try{
			m_socket = join( port, m_address );

			// ack timeout
			ackTimer = new Timer( TIME_ACK, new ActionListener(){
				@Override
				public void actionPerformed( ActionEvent e )
				{
					try{
						send( message );
					}catch( IOException e1 ){
						closeUDPConnection();
					}
				}
			} );

			HashSet<Character> char_used = new HashSet<Character>( 16 );

			boolean close = false;
			JSONObject msg;
			while(!close){
				msg = receive( 0 );
				if(msg == null) // useful when the timeout is greater than 0 
					continue;

				switch( decryptMessage( msg, "type" ).charAt( 0 ) ){
					case( Message.PARTIAL_RESULT ):
						// checks if the username is equals to the guesser one
						String guesser = decryptMessage( msg, "guesser" );
						if(ackTimer.isRunning() && username.equals( guesser )){ // this is the ACK message
							ackTimer.stop();
							send_again = true;
						}

						System.out.println( "GUESSER: " + guesser );

						String word = decryptMessage( msg, "word" );
						System.out.println( "<prompt>:: WORD: " + word );

						char c = decryptMessage( msg, "letter" ).charAt( 0 );
						if(!word.contains( c + "" ))
							char_used.add( c );

						// prints all the already used characters
						Iterator<Character> it = char_used.iterator();
						StringBuilder string = new StringBuilder( 32 );
						while(it.hasNext())
							string.append( it.next() );
						System.out.println( "<prompt>:: MISSES: " + string );
						System.out.print( "<prompt>:: " );

						break;
						
					case( Message.GO_ON ):
						// the Master has received too much time an old packet
						// we have to go on discarding it
						/*guesser = decryptMessage( msg, "guesser" );
						if(ackTimer.isRunning() && username.equals( guesser )){ // the old packet is mine
							ackTimer.stop();
							send_again = true;
						}*/
						if(ackTimer.isRunning()){
							ackTimer.stop();
							send_again = true;
						}

						break;

					case( Message.TIMEOUT ):
						System.out.println( "TIME'S OVER. MASTER WIN" );
						close = true;
						break;

					case( Message.END_OF_TRIALS ):
						System.out.println( "THE NUMBER OF TRIALS IS OVER. MASTER WIN" );
						close = true;
						break;

					case( Message.GUESSER_WIN ):
						System.out.println( "USER " + decryptMessage( msg, "guesser" ) + " WINS" );
						close = true;
						break;

					case( Message.END_GAME ):
						System.out.println( "THE MATCH IS FINISHED BECAUSE THE MASTER IS OUT" );
						close = true;
						break;
				}
			}
		}
		catch( IOException | ClassNotFoundException e ){
			e.printStackTrace();
		}

		ackTimer.stop();
		closeUDPConnection();

		in_game = false;
		send_again = false;
	}

	/** create a new UDP message
	 * 
	 * @param type		type of the message
	 * @param letter	played letter
	 * @param guesser	own username
	*/
	@SuppressWarnings("unchecked")
	private JSONObject createMessage( final char type, final String letter, final String guesser )
	{
		JSONObject object = new JSONObject();

		object.put( "type", ENCRYPTOR.encrypt( type + "" ) );
		object.put( "letter", ENCRYPTOR.encrypt( letter ) );
		object.put( "guesser", ENCRYPTOR.encrypt( guesser ) );
		object.put( "pkt_number", ENCRYPTOR.encrypt( packet_number + "" ) );

		return object;
	}
}