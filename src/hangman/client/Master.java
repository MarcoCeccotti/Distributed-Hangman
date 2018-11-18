/**
 * @author Marco Ceccotti
*/

package hangman.client;

import hangman.server.GuessWord;
import hangman.utils.HelpMessage;
import hangman.utils.Message;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

import javax.swing.Timer;

import org.json.simple.JSONObject;

public class Master extends Player
{
	/* manage the word to guess */
	private GuessWord gw;
	/* the associated timer */
	private Timer timer;

	/* maximum number of trials to guess the world */
	private static final int MAX_TRIALS = 10;
	/* number of trials after that we send a GO_ON message */
	private static final int MAX_DUPLICATED_MESSAGES = 3;

	public Master( final int customers, final Socket socket, final String username, final String guess_word ) throws IOException
	{
		super( customers, socket, null, username );

		gw = new GuessWord( guess_word );
	}

	@Override
	public void inputManagement( final Scanner scan, final String input ) throws SocketException, IOException
	{
		String command = input.toLowerCase();

		if(state == WAIT_MATCH){
			if(command.equals( "exit" )){
				System.out.print( "<prompt>:: DO YOU REALLY WANT TO LEAVE THE MATCH? (Y or N): " );
				if(scan.nextLine().equalsIgnoreCase( "y" )){
					try{ out.println( Message.EXIT ); }
					catch( Exception e ){}

					closed_external = false;
					closeTCPConnection();

					System.out.println( "<prompt>:: YOU ARE CLOSING THE GROUP..." );
				}
			}
			else if(command.equals( "help" ))
				HelpMessage.print( HelpMessage.MASTER );
			else if(!command.equals( "" ))
				System.out.println( "<prompt>:: UNKNOWN COMMAND " + input + ". TYPE help FOR COMMANDS LIST" );
		}
		else{ // START_MATCH
			if(command.equals( "exit" )){
				System.out.print( "<prompt>:: DO YOU REALLY WANT TO CLOSE THE MATCH? (Y or N): " );
				if(scan.nextLine().equalsIgnoreCase( "y" )){
					JSONObject message = createMessage( Message.END_GAME, null, null, null );
					send( message );
					closeUDPConnection();
				}
			}
			else if(command.equals( "help" ))
				HelpMessage.print( HelpMessage.MASTER_GAME );
			else if(!command.equals( "" ))
				System.out.println( "<prompt>:: UNKNOWN COMMAND " + input + ". TYPE help FOR COMMANDS LIST" );
		}
	}

	@Override
	public void run()
	{
		try{
			out.println( Message.MASTER + "" + customers );

			boolean close = false;
			Timer timer_match = null;
			JSONObject message;

			while(!close){
				// read data from the TCP socket
				if((message = (JSONObject) in.readObject()) == null){
					if(closed_external)
						System.out.println( "SERVER CONNECTION IS DOWN..." );

					if(timer_match != null)
						timer_match.stop();

					closeTCPConnection();
					break;
				}

				switch( (char) message.get( "type" ) ){
					case( Message.NO_MORE_MATCH ):
						System.out.println( "SORRY BUT THE SERVER CANNOT INSTANTIATE MORE MATCHES" );
						closeTCPConnection();
						close = true;

						break;

					case( Message.MATCH_CREATED ):
						in_game = true;

						timer_match = new Timer( TIME_MATCH, new ActionListener(){
							@Override
							public void actionPerformed( ActionEvent e )
							{
								out.println( Message.EXIT );
								System.out.println( "TIME IS OVER, YOUR MATCH WILL BE CLOSED" );

								closed_external = false;
								closeTCPConnection();
							}
						} );

						timer_match.start();

						System.out.println( "MATCH CREATED WITH SUCCESSFUL" );
						System.out.print( "<prompt>:: " );
						break;

					case( Message.START_MATCH ):
						timer_match.stop();
						state = START_MATCH;

						// obtains the match settings (multicast address, port and cryptographic key)
						m_address = InetAddress.getByName( (String) message.get( "address" ) );
						port = (int) message.get( "port" );
						key = (String) message.get( "key" );

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
	protected void playGame() throws ClassNotFoundException
	{
		ENCRYPTOR.setPassword( key );

		System.out.println( "THE MATCH IS STARTED" );

		try{
			m_socket = join( port, m_address );

			// used to close the worker-side TCP connection
			out.println( Message.START_MATCH );
			closeTCPConnection();

			// timer of the match
			timer = new Timer( TIME_MATCH, new ActionListener(){
				@Override
				public void actionPerformed( ActionEvent e )
				{
					if(!gw.isGuessed()){
						try{
							send( createMessage( Message.TIMEOUT, null, null, null ) );
							System.out.println( "<prompt>:: THE TIME IS OVER. YOU HAVE WIN" );
							closeUDPConnection();
						}catch( IOException e1 ){}

						timer.stop();
					}
				}
			} );

			timer.start();

			HashMap<String, GuesserInfo> players = new HashMap<String, GuesserInfo>( customers );
			//HashMap<String, Integer> players_pkt_number = new HashMap<String, Integer>( customers );
			HashSet<Character> char_used = new HashSet<Character>( 32 );
			boolean finish = false;
			int trials = MAX_TRIALS;
			GuesserInfo guesserInfo;
			//Integer pkt_number;

			while(!finish){
				System.out.print( "<prompt>:: " );

				JSONObject msg = receive( 0 );
				if(decryptMessage( msg, "type" ).charAt( 0 ) != Message.NEW_LETTER) // invalid message
					continue;

				// obtains the expected packet number of the guesser
				String guesser = decryptMessage( msg, "guesser" );
				if((guesserInfo = players.get( guesser )) == null)
					players.put( guesser, guesserInfo = new GuesserInfo() );

				int msg_pkt_number = Integer.parseInt( decryptMessage( msg, "pkt_number" ) );
				if(guesserInfo.getPktNumber() >= msg_pkt_number){
					// this is an old message sent by ack timeout
					if(guesserInfo.getOldMessages() == MAX_DUPLICATED_MESSAGES){
						JSONObject message = createMessage( Message.GO_ON, null, null, null );
						// send a GO_ON message to notify the guesser that we have received its last packet
						sendToGuesser( packet.getAddress().getHostAddress(), message );
					}
					else{
						guesserInfo.increaseOldMessages();
					}

					continue;
				}
				else{
					guesserInfo.setPktNumber( msg_pkt_number );
					//players.put( guesser, guesserInfo );
				}
				
				/*if((pkt_number = players_pkt_number.get( guesser )) == null)
					players_pkt_number.put( guesser, pkt_number = 0 );

				if(pkt_number >= msg_pkt_number)
					// this is an old message sent by ack timeout, then it must be discarded
					continue;
				else
					players_pkt_number.put( guesser, msg_pkt_number );*/

				char c = decryptMessage( msg, "letter" ).toLowerCase().charAt( 0 );
				// updates the current state ONLY if the character is selected for the first time
				if(!char_used.contains( c )){
					if(!gw.checkCharacter( c ))
						trials--;

					char_used.add( c );
				}

				// multicast send
				JSONObject message = createMessage( Message.PARTIAL_RESULT, c + "", guesser, gw.getPartialWord() );
				send( message );

				System.out.println( "<prompt>:: STATUS: " + gw.getPartialWord() + " / " + gw.getWord() + ", TRIALS: " + trials + "/" + MAX_TRIALS );

				if(gw.isGuessed()){
					timer.stop();
					message = createMessage( Message.GUESSER_WIN, null, guesser, null );
					send( message );
					finish = true;
					System.out.println( "<prompt>:: USER " + guesser + " HAS WIN" );
				}
				else{
					if(trials == 0){
						timer.stop();
						message = createMessage( Message.END_OF_TRIALS, null, null, null );
						send( message );
						finish = true;
						System.out.println( "<prompt>:: YOU HAVE WIN" );
					}
				}
			}
		}catch( IOException e ){
			e.printStackTrace();
		}

		closeUDPConnection();
		in_game = false;
	}

	/** create a new JSON UDP message
	 * 
	 * @param type		type of the message
	 * @param letter	played letter
	 * @param guesser	guesser username
	 * @param word		current state of the guess word
	*/
	@SuppressWarnings("unchecked")
	private JSONObject createMessage( final char type, final String letter, final String guesser, final String word )
	{
		JSONObject object = new JSONObject();

		object.put( "type", ENCRYPTOR.encrypt( type + "" ) );
		if(type == Message.PARTIAL_RESULT){
			object.put( "letter", ENCRYPTOR.encrypt( letter ) );
			object.put( "guesser", ENCRYPTOR.encrypt( guesser ) );
			object.put( "word", ENCRYPTOR.encrypt( word ) );
		}
		else{
			if(type == Message.GUESSER_WIN)
				object.put( "guesser", ENCRYPTOR.encrypt( guesser ) );
		}

		return object;
	}
}