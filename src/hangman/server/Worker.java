/**
 * @author Marco Ceccotti
*/

package hangman.server;

import hangman.utils.Match;
import hangman.utils.Message;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class Worker implements Runnable
{
	/* the associated socket */
	private Socket socket;
	/* socket input stream */
	private BufferedReader in;
	/* socket output stream */
	private ObjectOutputStream out;
	/* the associated username */
	private String username;
	/* determines if the user is a master */
	private boolean isMaster = false;
	/* the associated match */
	private Match match;
	/* list of matches */
	private static HashMap<String, Match> matches;
	/* determines if the worker has been initialized */
	private static boolean is_init = false;

	/* mutual exclusion object for match management */
	private static final ReentrantLock MATCH = new ReentrantLock();

	/** Creates a new instance of a worker thread.
	 *  Be sure to have invoked the Worker.init() method to initialize its internal structures
	 * 
	 * @throws InitException if the method Worker.init() has not been invoked
	*/
	public Worker( final Socket socket ) throws IOException, InitException
	{
		if(!is_init)
			throw new InitException();

		this.socket = socket;
		in = new BufferedReader( new InputStreamReader( socket.getInputStream() ) );
		out = new ObjectOutputStream( socket.getOutputStream() );
	}

	/** initialize the internal structures */
	public static void init() throws FileNotFoundException, IOException, ParseException
	{
		matches = new HashMap<String, Match>( Registry.max_matches );
		is_init = true;
		Match.init();
	}

	@Override
	public void run()
	{
		String message = null;
		boolean close = false;

		try{
			if((message = in.readLine()) == null)
				return;
			else{
				if(message.charAt( 0 ) == Message.HELLO)
					username = message.substring( 1 );
				else
					return;
			}

			Registry.users.get( username ).setInMatch( true );

			System.out.println( "[WORKER-" + username + "]: ACTIVATED" );

			while(!close && (message = in.readLine()) != null){
				switch( message.charAt( 0 ) ){
					case( Message.MASTER ):
						System.out.println( "[WORKER-" + username + "]: RECEIVED A MASTER REQUEST" );

						int result = addMatch( Integer.parseInt( message.substring( 1 ) ) );
						if(result == -1){
							out.writeObject( createMessage( Message.NO_MORE_MATCH ) );
							close = true;
						}
						else{
							out.writeObject( createMessage( Message.MATCH_CREATED ) );
							isMaster = true;
						}

						break;

					case( Message.GUESSER ):
						System.out.println( "[WORKER-" + username + "]: RECEIVED A GUESSER REQUEST" );

						switch( result = addUser( message.substring( 1 ) ) ){
							case( 0 ):
								System.out.println( "[WORKER-" + username + "]: ADDED TO MATCH" );
								out.writeObject( createMessage( Message.ADDED_TO_MATCH ) );
								break;

							case( -1 ):
								out.writeObject( createMessage( Message.MASTER_DOESNT_EXIST ) );
								close = true;
								break;

							case( -2 ):
								out.writeObject( createMessage( Message.MATCH_ALREADY_CLOSED ) );
								close = true;
								break;

							case( -3 ):
								out.writeObject( createMessage( Message.MATCH_FULL ) );
								close = true;
								break;
						}

						break;

					case( Message.START_MATCH ):
						System.out.println( "[WORKER-" + username + "]: START MATCH" );

						if(isMaster){
							match.startMatch();
							removeMatch( false, true );
						}

						close = true;

						break;

					case( Message.EXIT ):
						System.out.println( "[WORKER-" + username + "]: RECEIVED A LOGOUT REQUEST" );

						if(isMaster){
							try{ removeMatch( true, true ); }
							catch( RemoteException e1 ){}
						}
						else{
							try{ removeUser(); }
							catch( RemoteException e1 ){}
						}

						close = true;

						break;
				}
			}
		}catch( Exception e ){
			e.printStackTrace();
		}

		System.out.println( "[WORKER-" + username + "]: USER " + username + " IS OFFLINE" );

		if(!close && match != null){
			if(isMaster){
				try{ removeMatch( true, true ); }
				catch( RemoteException e1 ){}
			}
			else{
				try{ removeUser(); }
				catch( RemoteException e1 ){}
			}

			Registry.users.remove( username );
		}

		try{
			out.close();
			in.close();
			socket.close();
		}catch( IOException e ){}

		System.out.println( "[WORKER-" + username + "]: CLOSED" );
	}

	/** creates a new TCP message
	 * 
	 * @param type	message type
	*/
	@SuppressWarnings("unchecked")
	private JSONObject createMessage( final char type )
	{
		JSONObject object = new JSONObject();
		object.put( "type", type );

		return object;
	}

	/** creates a new match
	 * 
	 * @param users		number of requested users
	 * 
	 * @return 0 if the match is created, -1 otherwise (the limit is reached)
	*/
	private int addMatch( final int users ) throws RemoteException
	{
		match = new Match( users, username, out );

		MATCH.lock();

		if(matches.size() == Registry.max_matches){
			MATCH.unlock();
			match = null;
			return -1;
		}

		matches.put( username, match );

		MATCH.unlock();

		Registry.updateMatches( null );

		return 0;
	}

	/** removes the associated match
	 * 
	 * @param send_close	TRUE if the close message must be sent to the clients, FALSE otherwise
	 * @param send_update	TRUE if the update must be sent to the clients, FALSE otherwise
	*/
	private void removeMatch( final boolean send_close, final boolean send_update ) throws RemoteException
	{
		MATCH.lock();

		matches.remove( username );

		MATCH.unlock();

		if(send_close)
			match.closeMatch();

		if(send_update)
			Registry.updateMatches( null );
	}

	/** adds an user to the selected match
	 * 
	 * @param master  master username
	 * 
	 * @return 0 if the user is added to the match, -1 if the match doesn't exist,
	 * 		   -2 if the match was already closed, -3 if the match is full
	*/
	private int addUser( final String master ) throws IOException
	{
		MATCH.lock();

		match = matches.get( master );
		if(match == null){
			MATCH.unlock();
			return -1;
		}

		MATCH.unlock();

		int result = match.addUser( out );
		if(result != 0)
			return result;
		else
			Registry.updateMatches( null );

		return 0;
	}

	/** remove the associated user from the corresponding match */
	private void removeUser() throws RemoteException
	{
		match.removeUser( out );

		Registry.updateMatches( null );
	}

	/** returns the string representing the open matches */
	public static String getStringMatches()
	{
		StringBuilder m_string = new StringBuilder( 64 );
		int i = 0;

		MATCH.lock();

		m_string.append( "   " + matches.size() + " OPEN MATCHES" );

		Iterator<String> it = matches.keySet().iterator();
		while(it.hasNext()){
			Match m = matches.get( it.next() );
			m_string.append( "   MATCH " + (++i) + ": " + m.toString() + "\n" );
		}

		MATCH.unlock();

		return m_string.toString();
	}
}

class InitException extends Exception
{
	/* generated serial ID */
	private static final long serialVersionUID = -3871704546471923061L;

	public InitException()
	{
		super();
	}
}